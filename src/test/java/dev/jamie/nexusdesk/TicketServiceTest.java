package dev.jamie.nexusdesk;

import dev.jamie.nexusdesk.model.Account;
import dev.jamie.nexusdesk.service.*;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:tickets;DB_CLOSE_DELAY=-1", "NEXUSDESK_ADMIN_PASSWORD=Test-password-123"})
@Transactional
class TicketServiceTest {
    @Autowired TicketService tickets;
    @Autowired UserService users;
    @Autowired JdbcTemplate db;
    Account admin;
    Account employee;
    Account other;
    Account tech;
    @BeforeEach void setup() {
        admin = users.list().get(0);
        users.create(admin, "Alex", "alex@example.test", "EMPLOYEE", "Test-password-123");
        users.create(admin, "Sam", "sam@example.test", "EMPLOYEE", "Test-password-123");
        users.create(admin, "Morgan", "morgan@example.test", "TECHNICIAN", "Test-password-123");
        employee = named("Alex"); other = named("Sam"); tech = named("Morgan");
    }
    Account named(String name) { return users.list().stream().filter(u -> u.name().equals(name)).findFirst().orElseThrow(); }
    long create() { return tickets.create(employee, "Wi-Fi disconnects", "Drops during calls", "Network", "High"); }
    TicketPage all(Account actor) { return tickets.search(actor, TicketFilter.none(), 1, 20); }
    TicketPage find(Account actor, TicketFilter filter) { return tickets.search(actor, filter, 1, 20); }
    static TicketFilter title(String q) { return new TicketFilter(q, "", "", "", null, null, null, null); }
    static String field(Throwable error, String name) { return ((ValidationProblem) error).fields().get(name); }

    @Test void createsTicketAndHistoryTogether() {
        long id = create();
        assertThat(tickets.get(employee, id).creatorId()).isEqualTo(employee.id());
        assertThat(tickets.history(employee, id)).hasSize(1);
        assertThat(tickets.get(employee, id).status()).isEqualTo("Open");
    }
    @Test void rejectsEmptyAndOversizedTitlesWithFieldMessages() {
        assertThatThrownBy(() -> tickets.create(employee, "  ", "Details", "Hardware", "Low"))
                .isInstanceOf(ValidationProblem.class).satisfies(e -> assertThat(field(e, "title")).isEqualTo("Title is required."));
        assertThatThrownBy(() -> tickets.create(employee, "a".repeat(141), " ", "Nope", "Low"))
                .isInstanceOf(ValidationProblem.class)
                .satisfies(e -> assertThat(((ValidationProblem) e).fields()).containsOnlyKeys("title", "description", "category"));
        assertThat(tickets.create(employee, "a".repeat(140), "Details", "Hardware", "Low")).isPositive();
    }
    @Test void keepsEmployeeTicketsPrivate() {
        long id = create();
        assertThatThrownBy(() -> tickets.get(other, id)).isInstanceOf(Problem.class);
        assertThatThrownBy(() -> tickets.comment(other, id, "Hello")).isInstanceOf(Problem.class);
        assertThat(all(other).items()).isEmpty();
        // A creator filter can't widen what an employee sees.
        assertThat(find(other, new TicketFilter("", "", "", "", employee.id(), null, null, null)).total()).isZero();
    }
    @Test void employeeCannotManageTheirOwnTicket() {
        long id = create();
        assertThatThrownBy(() -> tickets.update(employee, id, 0, "Assigned", "High", tech.id(), ""))
                .isInstanceOf(Problem.class).extracting(e -> ((Problem) e).status()).isEqualTo(403);
    }

    // --- The assignment rule: Open means no technician, every other status needs one. ---

    @Test void choosingATechnicianButLeavingTheTicketOpenIsRejectedNotDropped() {
        long id = create();
        assertThatThrownBy(() -> tickets.update(tech, id, 0, "Open", "High", tech.id(), ""))
                .isInstanceOf(ValidationProblem.class)
                .satisfies(e -> assertThat(field(e, "assignee")).contains("change the status to Assigned").contains("Morgan"));
        var ticket = tickets.get(admin, id);
        assertThat(ticket.assigneeId()).isNull();
        assertThat(ticket.version()).isZero();
        assertThat(tickets.history(admin, id)).hasSize(1);
    }
    @Test void assigningMovesOpenToAssignedWithHistory() {
        long id = create();
        assertThat(tickets.update(tech, id, 0, "Assigned", "High", tech.id(), ""))
                .containsExactly("status Open → Assigned", "technician Unassigned → Morgan");
        assertThat(tickets.get(admin, id).assigneeId()).isEqualTo(tech.id());
        assertThat(tickets.history(admin, id)).extracting(a -> a.body())
                .contains("Status: Open → Assigned", "Assigned technician: Unassigned → Morgan");
    }
    @Test void forwardStatusesNeedATechnician() {
        long id = create();
        assertThatThrownBy(() -> tickets.update(tech, id, 0, "Assigned", "High", null, ""))
                .isInstanceOf(ValidationProblem.class).satisfies(e -> assertThat(field(e, "assignee")).contains("needs one"));
        tickets.update(tech, id, 0, "Assigned", "High", tech.id(), "");
        assertThatThrownBy(() -> tickets.update(tech, id, 1, "In Progress", "High", null, ""))
                .isInstanceOf(ValidationProblem.class);
    }
    @Test void rejectsInvalidAssigneesAndTransitionsBeforeWriting() {
        long id = create();
        assertThatThrownBy(() -> tickets.update(admin, id, 0, "Assigned", "High", employee.id(), ""))
                .isInstanceOf(ValidationProblem.class).satisfies(e -> assertThat(field(e, "assignee")).isEqualTo("Choose an active IT technician."));
        assertThatThrownBy(() -> tickets.update(admin, id, 0, "Assigned", "High", 999_999L, ""))
                .isInstanceOf(ValidationProblem.class);
        db.update("UPDATE users SET enabled=FALSE WHERE id=?", tech.id());
        assertThatThrownBy(() -> tickets.update(admin, id, 0, "Assigned", "High", tech.id(), ""))
                .isInstanceOf(ValidationProblem.class);
        assertThatThrownBy(() -> tickets.update(admin, id, 0, "Closed", "High", admin.id(), "Fixed"))
                .isInstanceOf(ValidationProblem.class).satisfies(e -> assertThat(field(e, "status")).contains("can't move from Open to Closed"));
        assertThatThrownBy(() -> tickets.update(admin, id, 0, "Done", "Urgent", admin.id(), ""))
                .isInstanceOf(ValidationProblem.class)
                .satisfies(e -> assertThat(((ValidationProblem) e).fields()).containsKeys("status", "priority"));
        assertThat(tickets.history(admin, id)).hasSize(1);
        assertThat(tickets.get(admin, id).version()).isZero();
    }
    @Test void resolvesClosesAndReopensWithoutLosingHistory() {
        long id = create();
        tickets.update(tech, id, 0, "Assigned", "High", tech.id(), "");
        assertThatThrownBy(() -> tickets.update(tech, id, 1, "Resolved", "High", tech.id(), " "))
                .isInstanceOf(ValidationProblem.class).satisfies(e -> assertThat(field(e, "resolution")).contains("Add resolution notes"));
        tickets.update(tech, id, 1, "Resolved", "High", tech.id(), "Replaced driver");
        assertThat(tickets.get(admin, id).resolvedAt()).isNotNull();
        tickets.update(tech, id, 2, "Closed", "High", tech.id(), "Replaced driver");
        assertThatThrownBy(() -> tickets.comment(employee, id, "Still broken")).isInstanceOf(ValidationProblem.class);
        // Reopening with the old technician still selected is refused, not silently cleared.
        assertThatThrownBy(() -> tickets.update(tech, id, 3, "Open", "High", tech.id(), "Replaced driver"))
                .isInstanceOf(ValidationProblem.class).satisfies(e -> assertThat(field(e, "assignee")).contains("Reopening"));
        tickets.update(tech, id, 3, "Open", "High", null, "Replaced driver");
        var reopened = tickets.get(admin, id);
        assertThat(reopened.status()).isEqualTo("Open");
        assertThat(reopened.resolvedAt()).isNull();
        assertThat(reopened.assigneeId()).isNull();
        assertThat(reopened.resolution()).isNull();
        // The earlier fix and every step stay in the history.
        assertThat(tickets.history(admin, id)).extracting(a -> a.body())
                .anyMatch(b -> b.contains("Replaced driver"))
                .contains("Status: Closed → Open", "Assigned technician: Morgan → Unassigned", "Status: Resolved → Closed");
        tickets.comment(employee, id, "It came back this morning.");
    }
    @Test void resolutionNotesOnAnUnfinishedTicketAreRejectedNotDiscarded() {
        long id = create();
        tickets.update(tech, id, 0, "Assigned", "High", tech.id(), "");
        assertThatThrownBy(() -> tickets.update(tech, id, 1, "In Progress", "High", tech.id(), "Swapped the cable"))
                .isInstanceOf(ValidationProblem.class).satisfies(e -> assertThat(field(e, "resolution")).contains("only when"));
        assertThat(tickets.get(admin, id).status()).isEqualTo("Assigned");
    }
    @Test void savingWithoutChangesWritesNothing() {
        long id = create();
        tickets.update(tech, id, 0, "Assigned", "High", tech.id(), "");
        assertThat(tickets.update(tech, id, 1, "Assigned", "High", tech.id(), "")).isEmpty();
        assertThat(tickets.get(admin, id).version()).isEqualTo(1);
        assertThat(tickets.history(admin, id)).hasSize(3);
    }
    @Test void aFinishedTicketCanBeClosedAfterItsTechnicianLeaves() {
        long id = create();
        tickets.update(tech, id, 0, "Assigned", "High", tech.id(), "");
        tickets.update(tech, id, 1, "Resolved", "High", tech.id(), "Replaced driver");
        users.edit(admin, tech.id(), tech.name(), tech.email(), tech.role(), false);
        tickets.update(admin, id, 2, "Closed", "High", tech.id(), "Replaced driver");
        assertThat(tickets.get(admin, id).status()).isEqualTo("Closed");
        // ...but a different, disabled technician still can't be newly assigned.
        long second = create();
        assertThatThrownBy(() -> tickets.update(admin, second, 0, "Assigned", "High", tech.id(), "")).isInstanceOf(ValidationProblem.class);
    }
    @Test void staleSaveDoesNotOverwriteNewWork() {
        long id = create();
        tickets.update(admin, id, 0, "Assigned", "High", tech.id(), "");
        assertThatThrownBy(() -> tickets.update(admin, id, 0, "Assigned", "Low", tech.id(), ""))
                .isInstanceOf(Problem.class).extracting(e -> ((Problem) e).status()).isEqualTo(409);
        assertThat(tickets.get(admin, id).priority()).isEqualTo("High");
    }
    @Test void aCommentMakesAnOpenEditFormStale() {
        long id = create();
        tickets.comment(employee, id, "Any update?");
        assertThatThrownBy(() -> tickets.update(tech, id, 0, "Assigned", "High", tech.id(), ""))
                .isInstanceOf(Problem.class).extracting(e -> ((Problem) e).status()).isEqualTo(409);
    }

    // --- Search and pagination ---

    @Test void treatsSearchAsDataAndEscapesWildcards() {
        create();
        assertThat(find(admin, title("' OR 1=1 --")).items()).isEmpty();
        assertThat(find(admin, title("%")).items()).isEmpty();
        assertThat(find(admin, new TicketFilter("wi-fi", "Open", "High", "Network", employee.id(), null, null, null)).items()).hasSize(1);
    }
    @Test void paginatesInStableOrderAndCountsTheWholeResult() {
        for (int i = 1; i <= 25; i++) tickets.create(employee, "Printer issue " + i, "Details", "Printer", i % 2 == 0 ? "High" : "Low");
        tickets.create(other, "Other person's printer", "Details", "Printer", "High");
        var first = tickets.search(admin, title("printer issue"), 1, 10);
        var third = tickets.search(admin, title("printer issue"), 3, 10);
        assertThat(first.total()).isEqualTo(25);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.items()).hasSize(10);
        assertThat(third.items()).hasSize(5);
        assertThat(third.firstItem()).isEqualTo(21);
        assertThat(third.lastItem()).isEqualTo(25);
        // Pages don't overlap.
        var second = tickets.search(admin, title("printer issue"), 2, 10);
        assertThat(second.items()).extracting(t -> t.id()).doesNotContainAnyElementsOf(first.items().stream().map(t -> t.id()).toList());
        // Filters apply to the count as well as the page.
        assertThat(tickets.search(admin, new TicketFilter("printer issue", "", "High", "", null, null, null, null), 1, 10).total()).isEqualTo(12);
        // Out-of-range pages land on the nearest real page.
        assertThat(tickets.search(admin, title("printer issue"), 99, 10).page()).isEqualTo(3);
        assertThat(tickets.search(admin, title("printer issue"), -4, 10).page()).isEqualTo(1);
        // An employee's count only includes their own tickets.
        assertThat(tickets.search(other, TicketFilter.none(), 1, 10).total()).isEqualTo(1);
    }
    @Test void reportsBadFiltersAsFieldErrors() {
        assertThatThrownBy(() -> find(admin, new TicketFilter("", "", "", "", null, null, LocalDate.now(), LocalDate.now().minusDays(1))))
                .isInstanceOf(ValidationProblem.class).satisfies(e -> assertThat(field(e, "to")).contains("end date"));
        assertThatThrownBy(() -> find(admin, title("x".repeat(141)))).isInstanceOf(ValidationProblem.class);
        assertThatThrownBy(() -> find(admin, new TicketFilter("", "Lost", "", "", null, null, null, null))).isInstanceOf(ValidationProblem.class);
    }
}
