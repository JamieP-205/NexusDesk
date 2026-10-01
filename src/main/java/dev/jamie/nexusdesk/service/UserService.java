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
        var users = db.query("SELECT " + COLUMNS + " FROM users WHERE id = ?", MAPPER, id);
        if (users.isEmpty()) throw Problem.missing();
        return users.get(0);
    }
    public List<Account> list() { return db.query("SELECT " + COLUMNS + " FROM users ORDER BY name", MAPPER); }
    public List<Account> active() { return list().stream().filter(Account::enabled).toList(); }
    public List<Account> technicians() { return active().stream().filter(Account::isStaff).toList(); }

    @Transactional
    public void create(Account actor, String name, String email, String role, String password) {
        Rules.admin(actor);
        insert(name, email, role, password);
    }
    public void insert(String name, String email, String role, String password) {
        name = Rules.text(name, "Name", 100);
        email = validEmail(email);
        Rules.choice(role, List.of("EMPLOYEE", "TECHNICIAN", "ADMIN"), "role");
        if (password == null || password.length() < 12 || password.length() > 128) {
            throw Problem.invalid("Use a password between 12 and 128 characters.");
        }
        db.update("INSERT INTO users(name,email,role,password_hash) VALUES (?,?,?,?)", name, email, role, passwords.encode(password));
    }
    @Transactional
    public void edit(Account actor, long id, String name, String email, String role, boolean enabled) {
        Rules.admin(actor);
        Rules.choice(role, List.of("EMPLOYEE", "TECHNICIAN", "ADMIN"), "role");
        // I lock the admin group so two requests can't remove the last admin at the same time.
        db.queryForList("SELECT id FROM users WHERE role = 'ADMIN' ORDER BY id FOR UPDATE");
        db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", id);
        Account old = find(id);
        if (id == actor.id() && (!enabled || !role.equals("ADMIN"))) {
            throw Problem.invalid("I can't disable or demote the account currently in use.");
        }
        if (old.isAdmin() && (!enabled || !role.equals("ADMIN")) &&
                db.queryForObject("SELECT COUNT(*) FROM users WHERE role='ADMIN' AND enabled=TRUE", Integer.class) <= 1) {
            throw Problem.invalid("Keep at least one enabled administrator.");
        }
        if ((!enabled || role.equals("EMPLOYEE")) && db.queryForObject(
                "SELECT COUNT(*) FROM tickets WHERE assignee_id=? AND status NOT IN ('Resolved','Closed')", Integer.class, id) > 0) {
            throw Problem.invalid("Reassign this person's active tickets first.");
        }
        db.update("UPDATE users SET name=?,email=?,role=?,enabled=? WHERE id=?",
                Rules.text(name, "Name", 100), validEmail(email), role, enabled, id);
    }
    private String validEmail(String email) {
        String clean = Rules.text(email, "Email", 160).toLowerCase(Locale.ROOT);
        if (!clean.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw Problem.invalid("Enter a valid email address.");
        return clean;
    }
}
