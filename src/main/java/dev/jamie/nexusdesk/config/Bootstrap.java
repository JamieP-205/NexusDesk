package dev.jamie.nexusdesk.config;

import dev.jamie.nexusdesk.service.UserService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Normal (non-demo) startup. Creates the first administrator from environment variables when the
 * database has no accounts, and refuses to run against a demo database.
 */
@Component
@Profile("!demo")
@Order(0)
public class Bootstrap implements ApplicationRunner {
    private final UserService users;
    private final DatabaseMode mode;
    private final Environment env;
    public Bootstrap(UserService users, DatabaseMode mode, Environment env) {
        this.users = users; this.mode = mode; this.env = env;
    }
    @Override
    public void run(ApplicationArguments args) {
        if (mode.isDemo()) {
            throw new IllegalStateException("This database was created by the demo profile and its accounts use public passwords. "
                    + "Start it with the demo profile, or set NEXUSDESK_DB_URL to a different database.");
        }
        if (!mode.hasNoUsers()) return;
        String password = env.getProperty("NEXUSDESK_ADMIN_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Set NEXUSDESK_ADMIN_PASSWORD (12-128 characters) before first startup. See README.");
        }
        if (password.equals(DemoData.PUBLIC_PASSWORD)) {
            throw new IllegalStateException("NEXUSDESK_ADMIN_PASSWORD is the public demo password. Choose your own password.");
        }
        users.insert("Administrator", env.getProperty("NEXUSDESK_ADMIN_EMAIL", "admin@example.test"), "ADMIN", password);
    }
}
