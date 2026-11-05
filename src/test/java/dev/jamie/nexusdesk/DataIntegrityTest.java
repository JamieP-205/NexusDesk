package dev.jamie.nexusdesk;

import dev.jamie.nexusdesk.model.Account;
import dev.jamie.nexusdesk.service.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/**
 * Deliberately NOT @Transactional: these tests need real commits and rollbacks, and real
 * concurrent transactions, which a test-wide transaction would hide.
 */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:integrity;DB_CLOSE_DELAY=-1", "NEXUSDESK_ADMIN_PASSWORD=Test-password-123"})
class DataIntegrityTest {
    @Autowired TicketService tickets;
    @Autowired AssetService assets;
    @Autowired UserService users;
    @Autowired JdbcTemplate db;
    Account admin, tech, alex, sam;

    @BeforeEach void setup() {
        admin = users.list().stream().filter(Account::isAdmin).findFirst().orElseThrow();
        if (users.list().size() == 1) {
            users.create(admin, "Morgan", "tech@example.test", "TECHNICIAN", "Test-password-123");
            users.create(admin, "Alex", "alex@example.test", "EMPLOYEE", "Test-password-123");
            users.create(admin, "Sam", "sam@example.test", "EMPLOYEE", "Test-password-123");
        }
        tech = byEmail("tech@example.test"); alex = byEmail("alex@example.test"); sam = byEmail("sam@example.test");
    }
    @AfterEach void dropTestConstraint() { db.execute("ALTER TABLE ticket_history DROP CONSTRAINT IF EXISTS test_fail_priority"); }
    Account byEmail(String email) { return users.list().stream().filter(a -> a.email().equals(email)).findFirst().orElseThrow(); }
    int historyCount(long id) { return db.queryForObject("SELECT COUNT(*) FROM ticket_history WHERE ticket_id=?", Integer.class, id); }

    @Test void aFailedHistoryWriteRollsBackTheTicketChange() {
        long id = tickets.create(alex, "Atomic check", "Details", "Other", "Low");
        // Make the second history insert fail, after the ticket row has already been updated.
        db.execute("ALTER TABLE ticket_history ADD CONSTRAINT test_fail_priority CHECK (event NOT LIKE 'Priority:%') NOCHECK");
        assertThatThrownBy(() -> tickets.update(tech, id, 0, "Assigned", "High", tech.id(), "")).isInstanceOf(Exception.class);
        var ticket = tickets.get(admin, id);
        assertThat(ticket.status()).isEqualTo("Open");
        assertThat(ticket.priority()).isEqualTo("Low");
        assertThat(ticket.assigneeId()).isNull();
        assertThat(ticket.version()).isZero();
        assertThat(historyCount(id)).isEqualTo(1);
    }

    @Test void twoTechniciansSavingTheSameVersionOnlyOneWins() throws Exception {
        long id = tickets.create(alex, "Race check", "Details", "Other", "Low");
        var outcomes = race(2, n -> tickets.update(n == 0 ? tech : admin, id, 0, "Assigned", n == 0 ? "High" : "Critical",
                n == 0 ? tech.id() : admin.id(), ""));
        assertThat(outcomes).filteredOn("ok"::equals).hasSize(1);
        assertThat(outcomes).filteredOn(o -> !o.equals("ok")).singleElement().asString().contains("409");
        var ticket = tickets.get(admin, id);
        assertThat(ticket.version()).isEqualTo(1);
        // Exactly one set of history rows: the winner's.
        assertThat(historyCount(id)).isEqualTo(4);
    }

    @Test void equipmentNeverHasTwoCurrentOwnersEvenUnderRacingRequests() throws Exception {
        long asset = assets.create(admin, "Laptop", "Dell", "Race", "RACE-" + UUID.randomUUID(), LocalDate.now());
        var outcomes = race(2, n -> assets.assign(admin, asset, n == 0 ? alex.id() : sam.id()));
        assertThat(outcomes).filteredOn("ok"::equals).hasSize(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM asset_assignments WHERE asset_id=? AND returned_at IS NULL", Integer.class, asset)).isEqualTo(1);
        assertThat(assets.get(admin, asset).status()).isEqualTo("Assigned");
    }

    @Test void returningThenReassigningKeepsTheFullHistory() {
        long asset = assets.create(admin, "Phone", "Samsung", "A55", "HIST-" + UUID.randomUUID(), LocalDate.now());
        assets.assign(admin, asset, alex.id());
        assets.returnAsset(admin, asset);
        assets.assign(admin, asset, sam.id());
        var history = assets.history(admin, asset);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).name()).isEqualTo("Sam");
        assertThat(history.get(0).returnedAt()).isNull();
        assertThat(history.get(1).name()).isEqualTo("Alex");
        assertThat(history.get(1).returnedAt()).isNotNull();
    }

    interface Action { void run(int n) throws Exception; }

    /** Starts the actions together and reports "ok" or the failure for each. */
    private List<String> race(int count, Action action) throws Exception {
        var pool = Executors.newFixedThreadPool(count);
        var start = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int n = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    try { action.run(n); return "ok"; }
                    catch (Problem p) { return p.status() + " " + p.getMessage(); }
                    catch (Exception e) { return e.getClass().getSimpleName(); }
                }));
            }
            start.countDown();
            List<String> results = new ArrayList<>();
            for (var f : futures) results.add(f.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
