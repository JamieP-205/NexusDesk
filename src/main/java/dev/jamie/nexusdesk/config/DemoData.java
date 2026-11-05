package dev.jamie.nexusdesk.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo profile only. Fills an empty database with fictional people, tickets and equipment,
 * all signing in with one public password. It never touches a database that wasn't created this way.
 */
@Component
@Profile("demo")
@Order(0)
public class DemoData implements ApplicationRunner {
    /** Public on purpose: it is printed in the README and on the demo sign-in page. */
    public static final String PUBLIC_PASSWORD = "Demo-password-123";
    private static final Logger LOG = LoggerFactory.getLogger(DemoData.class);
    private final DemoSeeder seeder;
    private final DatabaseMode mode;

    public DemoData(DemoSeeder seeder, DatabaseMode mode) {
        this.seeder = seeder; this.mode = mode;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (mode.hasNoUsers()) {
            seeder.seed();
            mode.markDemo();
            LOG.warn("Created fictional demo data. Every demo account uses the public password from the README.");
        } else if (mode.isDemo()) {
            if (!mode.isMarkedDemo()) mode.markDemo(); // a demo database from version 0.1.0
            LOG.warn("Using the existing demo database. Demo accounts use public passwords; don't add real data.");
        } else {
            throw new IllegalStateException("The demo profile only runs against an empty or demo database, "
                    + "so public demo accounts can't be added to real data. Check spring.datasource.url / NEXUSDESK_DB_URL.");
        }
    }
}
