package dev.jamie.nexusdesk.service;

import dev.jamie.nexusdesk.model.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetService {
    public static final List<String> STATUSES = List.of("Available", "Assigned", "Repair", "Retired");
    public static final String STALE = "Someone else saved this asset while you were editing it. Nothing was saved.";
    private final JdbcTemplate db;
    private final UserService users;
    private static final DataClassRowMapper<Asset> MAPPER = new DataClassRowMapper<>(Asset.class);
    private static final String SELECT = """
            SELECT a.*, aa.user_id AS owner_id, u.name AS owner_name FROM assets a
            LEFT JOIN asset_assignments aa ON aa.asset_id=a.id AND aa.returned_at IS NULL
            LEFT JOIN users u ON u.id=aa.user_id
            """;
    public AssetService(JdbcTemplate db, UserService users) { this.db = db; this.users = users; }
    public List<Asset> list(Account actor, Long owner) {
        if (!actor.isStaff()) owner = actor.id();
        return owner == null ? db.query(SELECT + " ORDER BY a.id DESC", MAPPER)
                : db.query(SELECT + " WHERE aa.user_id=? ORDER BY a.id DESC", MAPPER, owner);
    }
    public Asset get(Account actor, long id) {
        var rows = db.query(SELECT + " WHERE a.id=?", MAPPER, id);
        if (rows.isEmpty()) throw Problem.missing();
        var asset = rows.get(0);
        if (!actor.isStaff() && (asset.ownerId() == null || asset.ownerId() != actor.id())) throw Problem.missing();
        return asset;
    }
    @Transactional
    public long create(Account actor, String type, String manufacturer, String model, String serial, LocalDate purchaseDate) {
        Rules.admin(actor);
        var v = new Validation();
        Clean clean = validate(v, null, type, manufacturer, model, serial, purchaseDate);
        v.check();
        db.update("INSERT INTO assets(type,manufacturer,model,serial_number,purchase_date) VALUES (?,?,?,?,?)",
                type, clean.manufacturer(), clean.model(), clean.serial(), purchaseDate);
        return db.queryForObject("SELECT id FROM assets WHERE serial_number=?", Long.class, clean.serial());
    }
    /** Returns false when the submitted details match what is already stored, so nothing was written. */
    @Transactional
    public boolean edit(Account actor, long id, int version, String type, String manufacturer, String model,
                        String serial, LocalDate purchaseDate, String status) {
        Rules.admin(actor);
        lock(id);
        Asset old = get(actor, id);
        if (old.version() != version) throw Problem.conflict(STALE);
        var v = new Validation();
        Clean clean = validate(v, id, type, manufacturer, model, serial, purchaseDate);
        if (v.choice("status", status, STATUSES, "asset status") != null) {
            if (old.ownerId() != null && !status.equals("Assigned")) v.reject("status", "Return the asset to stock before changing its status.");
            if (old.ownerId() == null && status.equals("Assigned")) v.reject("status", "Use Assign equipment to give this asset an owner.");
        }
        v.check();
        if (type.equals(old.type()) && clean.manufacturer().equals(old.manufacturer()) && clean.model().equals(old.model())
                && clean.serial().equals(old.serialNumber()) && purchaseDate.equals(old.purchaseDate()) && status.equals(old.status())) {
            return false;
        }
        if (db.update("UPDATE assets SET type=?,manufacturer=?,model=?,serial_number=?,purchase_date=?,status=?,version=version+1 WHERE id=? AND version=?",
                type, clean.manufacturer(), clean.model(), clean.serial(), purchaseDate, status, id, version) != 1) {
            throw Problem.conflict(STALE);
        }
        return true;
    }
    @Transactional
    public void assign(Account actor, long id, Long owner) {
        Rules.admin(actor);
        lock(id);
        Asset asset = get(actor, id);
        if (asset.ownerId() != null) throw ValidationProblem.of("owner", "This asset is already assigned to " + asset.ownerName() + ". Return it to stock first.");
        if (!asset.status().equals("Available")) throw ValidationProblem.of("owner", "Only available assets can be assigned. This one is marked " + asset.status() + ".");
        if (owner == null) throw ValidationProblem.of("owner", "Choose who will receive this equipment.");
        db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", owner);
        Account person = users.findOrNull(owner);
        if (person == null || !person.enabled()) throw ValidationProblem.of("owner", "Choose an enabled person.");
        // The one_current_owner unique constraint is the last line of defence if two requests race past the lock.
        db.update("INSERT INTO asset_assignments(asset_id,user_id,assigned_by) VALUES (?,?,?)", id, owner, actor.id());
        db.update("UPDATE assets SET status='Assigned',version=version+1 WHERE id=?", id);
    }
    @Transactional
    public void returnAsset(Account actor, long id) {
        Rules.admin(actor);
        lock(id);
        if (get(actor, id).ownerId() == null) throw Problem.invalid("This asset is not assigned, so there is nothing to return.");
        // Returning closes the current assignment row; it never deletes it, so the history stays intact.
        db.update("UPDATE asset_assignments SET returned_at=CURRENT_TIMESTAMP WHERE asset_id=? AND returned_at IS NULL", id);
        db.update("UPDATE assets SET status='Available',version=version+1 WHERE id=?", id);
    }
    public List<Assignment> history(Account actor, long id) {
        Rules.staff(actor);
        get(actor, id);
        return db.query("""
                SELECT u.name, aa.assigned_at, aa.returned_at FROM asset_assignments aa
                JOIN users u ON aa.user_id=u.id WHERE aa.asset_id=? ORDER BY aa.id DESC
                """, new DataClassRowMapper<>(Assignment.class), id);
    }
    private void lock(long id) { db.queryForList("SELECT id FROM assets WHERE id=? FOR UPDATE", id); }

    private record Clean(String manufacturer, String model, String serial) {}
    private Clean validate(Validation v, Long id, String type, String maker, String model, String serial, LocalDate date) {
        v.choice("type", type, Rules.ASSET_TYPES, "asset type");
        String cleanMaker = v.text("manufacturer", maker, "Manufacturer", 80);
        String cleanModel = v.text("model", model, "Model", 100);
        String cleanSerial = v.text("serial", serial, "Serial number", 100);
        if (date == null) v.reject("purchaseDate", "Enter the purchase date.");
        else if (date.isAfter(LocalDate.now())) v.reject("purchaseDate", "Use a purchase date today or earlier.");
        if (cleanSerial != null && db.queryForObject("SELECT COUNT(*) FROM assets WHERE serial_number=? AND id<>?", Integer.class,
                cleanSerial, id == null ? -1L : id) > 0) {
            v.reject("serial", "Another asset already has this serial number.");
        }
        return new Clean(cleanMaker, cleanModel, cleanSerial);
    }
}
