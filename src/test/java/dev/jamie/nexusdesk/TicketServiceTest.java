package dev.jamie.nexusdesk;

import dev.jamie.nexusdesk.model.Account;
import dev.jamie.nexusdesk.service.*;
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
    @BeforeEach void setup() {
        admin = users.list().get(0);
        users.create(admin, "Alex", "alex@example.test", "EMPLOYEE", "Test-password-123");
        users.create(admin, "Sam", "sam@example.test", "EMPLOYEE", "Test-password-123");
        employee = users.list().stream().filter(u -> u.name().equals("Alex")).findFirst().orElseThrow();
        other = users.list().stream().filter(u -> u.name().equals("Sam")).findFirst().orElseThrow();
    }
    long create() { return tickets.create(employee, "Wi-Fi disconnects", "Drops during calls", "Network", "High"); }
    @Test void createsTicketAndHistoryTogether() {
        long id = create();
        assertThat(tickets.get(employee, id).creatorId()).isEqualTo(employee.id());
        assertThat(tickets.history(employee, id)).hasSize(1);
        assertThat(tickets.get(employee, id).status()).isEqualTo("Open");
    }
    @Test void rejectsEmptyAndOversizedTitles() {
        for (String title : new String[]{"  ", "a".repeat(141)}) {
            assertThatThrownBy(() -> tickets.create(employee, title, "Details", "Hardware", "Low")).isInstanceOf(Problem.class);
        }
        assertThat(tickets.create(employee, "a".repeat(140), "Details", "Hardware", "Low")).isPositive();
    }
    @Test void keepsEmployeeTicketsPrivate() {
        long id = create();
        assertThatThrownBy(() -> tickets.get(other, id)).isInstanceOf(Problem.class);
        assertThatThrownBy(() -> tickets.comment(other, id, "Hello")).isInstanceOf(Problem.class);
        assertThat(tickets.search(other, "", "", "", "", null, null, null, null)).isEmpty();
    }
    @Test void employeeCannotManageTheirOwnTicket() {
        long id = create();
        assertThatThrownBy(() -> tickets.update(employee, id, 0, "Assigned", "High", admin.id(), "")).isInstanceOf(Problem.class);
    }
    @Test void validatesAssigneeAndStateBeforeWriting() {
        long id = create();
        assertThatThrownBy(() -> tickets.update(admin, id, 0, "Assigned", "High", employee.id(), "")).isInstanceOf(Problem.class);
        assertThatThrownBy(() -> tickets.update(admin, id, 0, "Closed", "High", admin.id(), "Fixed")).isInstanceOf(Problem.class);
        assertThat(tickets.history(admin, id)).hasSize(1);
        assertThat(tickets.get(admin, id).version()).isZero();
    }
    @Test void resolvesClosesAndReopensWithoutLosingHistory() {
        long id = create();
        tickets.update(admin, id, 0, "Assigned", "High", admin.id(), "");
        assertThatThrownBy(() -> tickets.update(admin, id, 1, "Resolved", "High", admin.id(), " ")).isInstanceOf(Problem.class);
        tickets.update(admin, id, 1, "Resolved", "High", admin.id(), "Replaced driver");
        assertThat(tickets.get(admin, id).resolvedAt()).isNotNull();
        tickets.update(admin, id, 2, "Closed", "High", admin.id(), "Replaced driver");
        assertThatThrownBy(() -> tickets.comment(employee, id, "Still broken")).isInstanceOf(Problem.class);
        tickets.update(admin, id, 3, "Closed", "High", admin.id(), "Replaced driver");
        tickets.update(admin, id, 4, "Open", "High", null, "");
        assertThat(tickets.get(admin, id).resolvedAt()).isNull();
        assertThat(tickets.get(admin, id).assigneeId()).isNull();
        assertThat(tickets.history(admin, id).size()).isGreaterThan(5);
        assertThat(tickets.history(admin, id)).anyMatch(a -> a.body().contains("Replaced driver"));
    }
    @Test void staleSaveDoesNotOverwriteNewWork() {
        long id = create();
        tickets.update(admin, id, 0, "Assigned", "High", admin.id(), "");
        assertThatThrownBy(() -> tickets.update(admin, id, 0, "Assigned", "Low", admin.id(), "")).isInstanceOf(Problem.class);
        assertThat(tickets.get(admin, id).priority()).isEqualTo("High");
    }
    @Test void treatsSearchAsDataAndEscapesWildcards() {
        create();
        assertThat(tickets.search(admin, "' OR 1=1 --", "", "", "", null, null, null, null)).isEmpty();
        assertThat(tickets.search(admin, "%", "", "", "", null, null, null, null)).isEmpty();
        assertThat(tickets.search(admin, "wi-fi", "Open", "High", "Network", employee.id(), null, null, null)).hasSize(1);
    }
}
