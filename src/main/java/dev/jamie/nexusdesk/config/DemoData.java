package dev.jamie.nexusdesk.config;

import dev.jamie.nexusdesk.model.Account;
import dev.jamie.nexusdesk.service.*;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate db;
    private final UserService users;
    private final TicketService tickets;
    private final AssetService assets;
    private final DatabaseMode mode;

    public DemoData(JdbcTemplate db, UserService users, TicketService tickets, AssetService assets, DatabaseMode mode) {
        this.db = db; this.users = users; this.tickets = tickets; this.assets = assets; this.mode = mode;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (mode.hasNoUsers()) {
            seed();
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

    private Account person(String email) {
        return users.list().stream().filter(a -> a.email().equals(email)).findFirst().orElseThrow();
    }

    private void seed() {
        users.insert("Jordan Hale", "admin@example.test", "ADMIN", PUBLIC_PASSWORD);
        Account admin = person("admin@example.test");
        users.create(admin, "Morgan Reid", "tech@example.test", "TECHNICIAN", PUBLIC_PASSWORD);
        users.create(admin, "Priya Shah", "priya@example.test", "TECHNICIAN", PUBLIC_PASSWORD);
        users.create(admin, "Alex Murphy", "employee@example.test", "EMPLOYEE", PUBLIC_PASSWORD);
        users.create(admin, "Casey Ward", "casey@example.test", "EMPLOYEE", PUBLIC_PASSWORD);
        users.create(admin, "Sam Okafor", "sam@example.test", "EMPLOYEE", PUBLIC_PASSWORD);
        users.create(admin, "Robin Lee", "robin@example.test", "EMPLOYEE", PUBLIC_PASSWORD);
        Account morgan = person("tech@example.test"), priya = person("priya@example.test");
        Account[] requesters = {person("employee@example.test"), person("casey@example.test"), person("sam@example.test"), person("robin@example.test")};
        Account[] techs = {morgan, priya};

        // title, category, priority, final status, requester index, days ago
        String[][] examples = {
            {"Wi-Fi drops during video calls", "Network", "High", "In Progress", "0", "1"},
            {"New starter needs an email account", "Account", "Medium", "Assigned", "1", "1"},
            {"Meeting room display has no signal", "Hardware", "High", "Waiting", "2", "2"},
            {"Shared drive is unavailable", "Network", "Critical", "Open", "1", "0"},
            {"Install the approved design software", "Software", "Low", "Open", "0", "3"},
            {"Printer is leaving marks on pages", "Printer", "Medium", "Assigned", "2", "4"},
            {"Laptop battery is not charging", "Hardware", "High", "Resolved", "0", "5"},
            {"Keyboard replacement request", "Hardware", "Low", "Closed", "1", "16"},
            {"Account locked after password change", "Account", "Medium", "Resolved", "2", "2"},
            {"VPN asks for sign-in every hour", "Network", "Medium", "In Progress", "3", "6"},
            {"Second monitor flickers", "Hardware", "Low", "Waiting", "0", "7"},
            {"Spreadsheet add-in won't load", "Software", "Medium", "Open", "2", "1"},
            {"Phone isn't receiving work email", "Account", "High", "Assigned", "1", "3"},
            {"Scanner saves blank pages", "Printer", "Low", "Closed", "3", "20"},
            {"Request access to the finance folder", "Account", "Medium", "Resolved", "0", "9"},
            {"Laptop fan is very loud", "Hardware", "Low", "Open", "1", "8"},
            {"Video calls have no microphone audio", "Software", "High", "In Progress", "2", "2"},
            {"Guest Wi-Fi password for visitors", "Network", "Low", "Closed", "1", "25"},
            {"Docking station not detecting laptop", "Hardware", "Medium", "Assigned", "3", "5"},
            {"Calendar invites show the wrong time", "Software", "Medium", "Resolved", "0", "12"},
            {"Printer on floor 2 is offline", "Printer", "High", "Open", "2", "0"},
            {"Need a larger mailbox", "Account", "Low", "Waiting", "1", "11"},
            {"Laptop won't wake from sleep", "Hardware", "Medium", "Closed", "0", "30"},
            {"Browser keeps opening old homepage", "Software", "Low", "Resolved", "3", "4"},
            {"Network cable at desk 14 is damaged", "Network", "Medium", "Open", "0", "2"},
            {"Antivirus warning on shared laptop", "Software", "Critical", "In Progress", "1", "1"}
        };
        String fix = "Checked the equipment, applied the fix and confirmed it works with the requester.";
        for (int i = 0; i < examples.length; i++) {
            var row = examples[i];
            Account creator = requesters[Integer.parseInt(row[4])];
            Account tech = techs[i % 2];
            long id = tickets.create(creator, row[0], row[0] + ".\n\nThis is fictional demo data. "
                    + "It started this morning; I've restarted once and it's still happening.", row[1], row[2]);
            String target = row[3];
            int version = 0;
            if (!target.equals("Open")) {
                tickets.update(admin, id, version++, "Assigned", row[2], tech.id(), "");
                if (target.equals("In Progress") || target.equals("Waiting")) {
                    tickets.update(tech, id, version++, target, row[2], tech.id(), "");
                } else if (target.equals("Resolved") || target.equals("Closed")) {
                    tickets.update(tech, id, version++, "In Progress", row[2], tech.id(), "");
                    tickets.update(tech, id, version++, "Resolved", row[2], tech.id(), fix);
                    if (target.equals("Closed")) tickets.update(tech, id, version++, "Closed", row[2], tech.id(), fix);
                }
            }
            if (i == 0) tickets.comment(tech, id, "I've checked the access point. Next I'll test the laptop's wireless driver.");
            if (i == 0) tickets.comment(creator, id, "Thanks. It happened again at 11:00 during the team call.");
            if (i == 2) tickets.comment(tech, id, "Waiting for a replacement HDMI adapter to arrive.");
            backdate(id, Integer.parseInt(row[5]) * 1440 + i * 37);
        }

        String[][] equipment = {{"Laptop", "Lenovo", "ThinkPad E14"}, {"Laptop", "Dell", "Latitude 5450"},
                {"Monitor", "Dell", "P2425H"}, {"Phone", "Samsung", "Galaxy A55"},
                {"Keyboard", "Logitech", "K120"}, {"Monitor", "HP", "E24 G5"},
                {"Tablet", "Apple", "iPad 10th gen"}, {"Desktop", "HP", "Pro Mini 400"}};
        long[] ids = new long[equipment.length];
        for (int i = 0; i < equipment.length; i++) {
            var item = equipment[i];
            ids[i] = assets.create(admin, item[0], item[1], item[2], "DEMO-" + (1001 + i), LocalDate.now().minusMonths(3 + i));
        }
        assets.assign(admin, ids[0], requesters[0].id());
        assets.assign(admin, ids[1], requesters[1].id());
        assets.assign(admin, ids[3], requesters[2].id());
        // A returned laptop keeps its history: Sam had it before Casey's current one.
        assets.assign(admin, ids[7], requesters[2].id());
        assets.returnAsset(admin, ids[7]);
        assets.edit(admin, ids[5], 0, "Monitor", "HP", "E24 G5", "DEMO-1006", LocalDate.now().minusMonths(8), "Repair");
        assets.edit(admin, ids[4], 0, "Keyboard", "Logitech", "K120", "DEMO-1005", LocalDate.now().minusMonths(7), "Retired");
        // A disabled account shows how leavers keep their records but lose access.
        Account robin = person("robin@example.test");
        users.edit(admin, robin.id(), robin.name(), robin.email(), robin.role(), false);
    }

    /** Spreads the sample tickets over the past few weeks so the dates and filters look realistic. */
    private void backdate(long ticket, int minutes) {
        db.update("""
                UPDATE tickets SET created_at=DATEADD('MINUTE', -?, created_at), updated_at=DATEADD('MINUTE', -?, updated_at),
                resolved_at=CASE WHEN resolved_at IS NULL THEN NULL ELSE DATEADD('MINUTE', -?, resolved_at) END WHERE id=?
                """, minutes, minutes, minutes, ticket);
        db.update("UPDATE ticket_history SET created_at=DATEADD('MINUTE', -?, created_at) WHERE ticket_id=?", minutes, ticket);
        db.update("UPDATE comments SET created_at=DATEADD('MINUTE', -?, created_at) WHERE ticket_id=?", minutes, ticket);
    }
}
