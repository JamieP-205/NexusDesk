package dev.jamie.nexusdesk.config;

import dev.jamie.nexusdesk.service.UserService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@org.springframework.core.annotation.Order(0)
public class Bootstrap implements ApplicationRunner {
    private final UserService users;
    private final JdbcTemplate db;
    private final Environment env;
    public Bootstrap(UserService users, JdbcTemplate db, Environment env) {
        this.users = users; this.db = db; this.env = env;
    }
    @Override
    public void run(ApplicationArguments args) {
        if (db.queryForObject("SELECT COUNT(*) FROM users", Integer.class) != 0) return;
        String password = env.getProperty("NEXUSDESK_ADMIN_PASSWORD");
        if (password == null) throw new IllegalStateException("Set NEXUSDESK_ADMIN_PASSWORD (12–128 characters) before first startup. See README.");
        users.insert("Administrator", env.getProperty("NEXUSDESK_ADMIN_EMAIL", "admin@example.test"), "ADMIN", password);
    }
}
