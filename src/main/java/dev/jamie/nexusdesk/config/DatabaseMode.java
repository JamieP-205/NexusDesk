package dev.jamie.nexusdesk.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Tells a demo database apart from a normal one, so their accounts can never be mixed. */
@Component
public class DatabaseMode {
    private final JdbcTemplate db;

    public DatabaseMode(JdbcTemplate db) { this.db = db; }

    public boolean hasNoUsers() { return db.queryForObject("SELECT COUNT(*) FROM users", Integer.class) == 0; }

    public boolean isMarkedDemo() {
        return db.queryForList("SELECT setting FROM app_metadata WHERE name='mode'", String.class).contains("demo");
    }

    /**
     * Demo databases made by version 0.1.0 have no marker. They always contain the demo technician
     * and the DEMO-1001 laptop, which a real deployment wouldn't, so that pair identifies them.
     */
    public boolean looksLikeUnmarkedDemo() {
        return db.queryForObject("SELECT COUNT(*) FROM users WHERE email IN ('tech@example.test','employee@example.test')", Integer.class) == 2
                && db.queryForObject("SELECT COUNT(*) FROM assets WHERE serial_number='DEMO-1001'", Integer.class) == 1;
    }

    public boolean isDemo() { return isMarkedDemo() || looksLikeUnmarkedDemo(); }

    public void markDemo() {
        db.update("MERGE INTO app_metadata(name, setting) KEY(name) VALUES ('mode', 'demo')");
    }
}
