package dev.jamie.nexusdesk.config;

import dev.jamie.nexusdesk.service.*;
import java.time.LocalDate;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
@Order(1)
public class DemoData implements ApplicationRunner {
    private final JdbcTemplate db;
    private final UserService users;
    private final TicketService tickets;
    private final AssetService assets;
    private final Environment env;
    public DemoData(JdbcTemplate db, UserService users, TicketService tickets, AssetService assets, Environment env) {
        this.db = db; this.users = users; this.tickets = tickets; this.assets = assets; this.env = env;
    }
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (db.queryForObject("SELECT COUNT(*) FROM tickets", Integer.class) > 0) return;
        var admin = users.list().stream().filter(a -> a.isAdmin()).findFirst().orElseThrow();
        String password = env.getRequiredProperty("NEXUSDESK_ADMIN_PASSWORD");
        users.create(admin, "Morgan Reid", "tech@example.test", "TECHNICIAN", password);
        users.create(admin, "Alex Murphy", "employee@example.test", "EMPLOYEE", password);
        users.create(admin, "Casey Ward", "casey@example.test", "EMPLOYEE", password);
        var tech = users.list().stream().filter(a -> a.email().equals("tech@example.test")).findFirst().orElseThrow();
        var alex = users.list().stream().filter(a -> a.email().equals("employee@example.test")).findFirst().orElseThrow();
        var casey = users.list().stream().filter(a -> a.email().equals("casey@example.test")).findFirst().orElseThrow();
        String[][] examples = {
            {"Wi-Fi drops during video calls", "Network", "High", "In Progress"},
            {"New starter needs an email account", "Account", "Medium", "Assigned"},
            {"Meeting room display has no signal", "Hardware", "High", "Waiting"},
            {"Shared drive is unavailable", "Network", "Critical", "Open"},
            {"Install the approved design software", "Software", "Low", "Open"},
            {"Printer is leaving marks on pages", "Printer", "Medium", "Assigned"},
            {"Laptop battery is not charging", "Hardware", "High", "Resolved"},
            {"Keyboard replacement request", "Hardware", "Low", "Closed"},
            {"Account locked after password change", "Account", "Medium", "Resolved"}
        };
        for (int i = 0; i < examples.length; i++) {
            var row = examples[i]; var creator = i % 2 == 0 ? alex : casey;
            long id = tickets.create(creator, row[0], "Sample request for the NexusDesk demo.\n\n" + row[0] + ". Please check the equipment and let me know what to try next.", row[1], row[2]);
            if (!row[3].equals("Open")) {
                tickets.update(admin, id, 0, "Assigned", row[2], tech.id(), "");
                if (!row[3].equals("Assigned")) {
                    String step = row[3].equals("Closed") ? "Resolved" : row[3];
                    tickets.update(tech, id, 1, step, row[2], tech.id(), "Checked the equipment, applied the fix and confirmed it works with the requester.");
                    if (row[3].equals("Closed")) tickets.update(tech, id, 2, "Closed", row[2], tech.id(), "Replacement supplied and confirmed working.");
                }
            }
            if (i == 0) tickets.comment(tech, id, "I've checked the access point. Next I'll test the laptop's wireless driver.");
        }
        String[][] equipment = {{"Laptop", "Lenovo", "ThinkPad E14"}, {"Laptop", "Dell", "Latitude 5450"},
                {"Monitor", "Dell", "P2425H"}, {"Phone", "Samsung", "Galaxy A55"},
                {"Keyboard", "Logitech", "K120"}, {"Monitor", "HP", "E24 G5"}};
        for (int i = 0; i < equipment.length; i++) {
            var item = equipment[i];
            assets.create(admin, item[0], item[1], item[2], "DEMO-" + (1001 + i), LocalDate.now().minusMonths(3));
        }
        var inventory = assets.list(admin, null);
        assets.assign(admin, inventory.get(0).id(), alex.id());
        assets.assign(admin, inventory.get(1).id(), casey.id());
    }
}
