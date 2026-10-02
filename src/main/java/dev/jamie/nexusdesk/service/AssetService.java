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
    public void create(Account actor, String type, String manufacturer, String model, String serial, LocalDate purchaseDate) {
        Rules.admin(actor);
        validate(type, manufacturer, model, serial, purchaseDate);
        db.update("INSERT INTO assets(type,manufacturer,model,serial_number,purchase_date) VALUES (?,?,?,?,?)",
                type, manufacturer.strip(), model.strip(), serial.strip(), purchaseDate);
    }
    @Transactional
    public void edit(Account actor, long id, int version, String type, String manufacturer, String model,
                     String serial, LocalDate purchaseDate, String status) {
        Rules.admin(actor);
        validate(type, manufacturer, model, serial, purchaseDate);
        lock(id);
        Asset old = get(actor, id);
        Rules.choice(status, List.of("Available", "Assigned", "Repair", "Retired"), "asset status");
        if (old.ownerId() != null && !status.equals("Assigned")) throw Problem.invalid("Return the asset before changing its status.");
        if (old.ownerId() == null && status.equals("Assigned")) throw Problem.invalid("Use Assign to give this asset an owner.");
        if (db.update("UPDATE assets SET type=?,manufacturer=?,model=?,serial_number=?,purchase_date=?,status=?,version=version+1 WHERE id=? AND version=?",
                type, manufacturer.strip(), model.strip(), serial.strip(), purchaseDate, status, id, version) != 1) {
            throw new Problem(409, "This asset changed. Reload it before saving.");
        }
    }
    @Transactional
    public void assign(Account actor, long id, long owner) {
        Rules.admin(actor);
        lock(id);
        Asset asset = get(actor, id);
        if (!asset.status().equals("Available") || asset.ownerId() != null) throw Problem.invalid("Only available assets can be assigned.");
        db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", owner);
        Account person = users.find(owner);
        if (!person.enabled()) throw Problem.invalid("Choose an enabled user.");
        db.update("INSERT INTO asset_assignments(asset_id,user_id,assigned_by) VALUES (?,?,?)", id, owner, actor.id());
        db.update("UPDATE assets SET status='Assigned',version=version+1 WHERE id=?", id);
    }
    @Transactional
    public void returnAsset(Account actor, long id) {
        Rules.admin(actor);
        lock(id);
        if (get(actor, id).ownerId() == null) throw Problem.invalid("This asset is not assigned.");
        db.update("UPDATE asset_assignments SET returned_at=CURRENT_TIMESTAMP WHERE asset_id=? AND returned_at IS NULL", id);
        db.update("UPDATE assets SET status='Available',version=version+1 WHERE id=?", id);
    }
    public List<java.util.Map<String, Object>> history(Account actor, long id) {
        Rules.staff(actor);
        get(actor, id);
        return db.queryForList("""
                SELECT u.name, aa.assigned_at, aa.returned_at FROM asset_assignments aa
                JOIN users u ON aa.user_id=u.id WHERE aa.asset_id=? ORDER BY aa.id DESC
                """, id);
    }
    private void lock(long id) { db.queryForList("SELECT id FROM assets WHERE id=? FOR UPDATE", id); }
    private void validate(String type, String maker, String model, String serial, LocalDate date) {
        Rules.choice(type, Rules.ASSET_TYPES, "asset type"); Rules.text(maker, "Manufacturer", 80);
        Rules.text(model, "Model", 100); Rules.text(serial, "Serial number", 100);
        if (date == null || date.isAfter(LocalDate.now())) throw Problem.invalid("Use a purchase date today or earlier.");
    }
}
