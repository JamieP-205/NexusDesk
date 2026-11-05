package dev.jamie.nexusdesk.service;

import dev.jamie.nexusdesk.model.Account;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    public static final List<String> ROLES = List.of("EMPLOYEE", "TECHNICIAN", "ADMIN");
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private static final DataClassRowMapper<Account> MAPPER = new DataClassRowMapper<>(Account.class);
    private static final String COLUMNS = "id, name, email, role, enabled";

    public UserService(JdbcTemplate db, PasswordEncoder passwords) {
        this.db = db;
        this.passwords = passwords;
    }
    public Account current(Authentication auth) {
        if (auth == null) throw new Problem(401, "Please sign in again.");
        var users = db.query("SELECT " + COLUMNS + " FROM users WHERE email = ? AND enabled = TRUE", MAPPER, auth.getName());
        if (users.isEmpty()) throw new Problem(401, "Please sign in again.");
        return users.get(0);
    }
    public Account find(long id) {
        Account account = findOrNull(id);
        if (account == null) throw Problem.missing();
        return account;
    }
    public Account findOrNull(long id) {
        var users = db.query("SELECT " + COLUMNS + " FROM users WHERE id = ?", MAPPER, id);
        return users.isEmpty() ? null : users.get(0);
    }
    public List<Account> list() { return db.query("SELECT " + COLUMNS + " FROM users ORDER BY name", MAPPER); }
    public List<Account> active() { return list().stream().filter(Account::enabled).toList(); }
    public List<Account> technicians() { return active().stream().filter(Account::isStaff).toList(); }

    @Transactional
    public void create(Account actor, String name, String email, String role, String password) {
        Rules.admin(actor);
        insert(name, email, role, password);
    }

    /** Used by create() and by first-start setup, which runs before any administrator exists. */
    public void insert(String name, String email, String role, String password) {
        var v = new Validation();
        String cleanName = v.text("name", name, "Name", 100);
        String cleanEmail = validEmail(v, email);
        v.choice("role", role, ROLES, "role");
        if (password == null || password.length() < 12 || password.length() > 128) {
            v.reject("password", "Use a password between 12 and 128 characters.");
        }
        if (cleanEmail != null && emailTaken(cleanEmail, null)) v.reject("email", "Another account already uses this email address.");
        v.check();
        // The UNIQUE constraint on users.email still protects against two simultaneous requests.
        db.update("INSERT INTO users(name,email,role,password_hash) VALUES (?,?,?,?)", cleanName, cleanEmail, role, passwords.encode(password));
    }

    @Transactional
    public void edit(Account actor, long id, String name, String email, String role, boolean enabled) {
        Rules.admin(actor);
        // I lock the admin group so two requests can't remove the last admin at the same time.
        db.queryForList("SELECT id FROM users WHERE role = 'ADMIN' ORDER BY id FOR UPDATE");
        db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", id);
        Account old = find(id);
        var v = new Validation();
        String cleanName = v.text("name", name, "Name", 100);
        String cleanEmail = validEmail(v, email);
        v.choice("role", role, ROLES, "role");
        if (cleanEmail != null && emailTaken(cleanEmail, id)) v.reject("email", "Another account already uses this email address.");
        if (!v.has("role")) {
            if (id == actor.id() && !role.equals("ADMIN")) v.reject("role", "You can't remove administrator access from the account you're signed in with.");
            if (id == actor.id() && !enabled) v.reject("enabled", "You can't disable the account you're signed in with.");
            if (old.isAdmin() && (!enabled || !role.equals("ADMIN")) &&
                    db.queryForObject("SELECT COUNT(*) FROM users WHERE role='ADMIN' AND enabled=TRUE", Integer.class) <= 1) {
                v.reject(enabled ? "role" : "enabled", "Keep at least one enabled administrator.");
            }
            if ((!enabled || role.equals("EMPLOYEE")) && db.queryForObject(
                    "SELECT COUNT(*) FROM tickets WHERE assignee_id=? AND status NOT IN ('Resolved','Closed')", Integer.class, id) > 0) {
                v.reject(enabled ? "role" : "enabled", "Reassign this person's active tickets first.");
            }
        }
        v.check();
        db.update("UPDATE users SET name=?,email=?,role=?,enabled=? WHERE id=?", cleanName, cleanEmail, role, enabled, id);
    }

    private boolean emailTaken(String email, Long except) {
        return db.queryForObject("SELECT COUNT(*) FROM users WHERE email=? AND id<>?", Integer.class,
                email, except == null ? -1L : except) > 0;
    }
    private String validEmail(Validation v, String email) {
        String clean = v.text("email", email, "Email", 160);
        if (clean == null) return null;
        clean = clean.toLowerCase(Locale.ROOT);
        if (!clean.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            v.reject("email", "Enter an email address like name@example.com.");
            return null;
        }
        return clean;
    }
}
