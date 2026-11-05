package dev.jamie.nexusdesk;

import dev.jamie.nexusdesk.model.Account;
import dev.jamie.nexusdesk.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:users;DB_CLOSE_DELAY=-1", "NEXUSDESK_ADMIN_PASSWORD=Test-password-123"})
@Transactional
class UserServiceTest {
    @Autowired UserService users;
    @Autowired TicketService tickets;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    Account admin;
    @BeforeEach void setup() { admin = users.list().stream().filter(Account::isAdmin).findFirst().orElseThrow(); }
    @Test void storesSaltedHashesRatherThanPlaintext() {
        users.create(admin, "Alex", "alex@example.test", "EMPLOYEE", "Test-password-123");
        String first = db.queryForObject("SELECT password_hash FROM users WHERE id=?", String.class, admin.id());
        String second = db.queryForObject("SELECT password_hash FROM users WHERE email=?", String.class, "alex@example.test");
        assertThat(first).isNotEqualTo(second).doesNotContain("Test-password-123");
        assertThat(second).startsWith("{pbkdf2-sha256}");
        assertThat(passwords.matches("Test-password-123", second)).isTrue();
    }
    @Test void rejectsDuplicateEmailsIgnoringCaseAndInvalidInput() {
        assertThatThrownBy(() -> users.create(admin, "Other", "ADMIN@EXAMPLE.TEST", "EMPLOYEE", "Test-password-123"))
                .isInstanceOf(ValidationProblem.class)
                .satisfies(e -> assertThat(((ValidationProblem) e).fields().get("email")).contains("already uses"));
        assertThatThrownBy(() -> db.update("INSERT INTO users(name,email,role,password_hash) VALUES ('X','admin@example.test','EMPLOYEE','x')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> users.create(admin, "Other", "bad-email", "EMPLOYEE", "Test-password-123")).isInstanceOf(Problem.class);
        assertThatThrownBy(() -> users.create(admin, "Other", "other@example.test", "EMPLOYEE", "short")).isInstanceOf(Problem.class);
        assertThatThrownBy(() -> users.create(admin, " ", "bad", "BOSS", "short"))
                .isInstanceOf(ValidationProblem.class)
                .satisfies(e -> assertThat(((ValidationProblem) e).fields()).containsOnlyKeys("name", "email", "role", "password"));
    }
    @Test void cannotDisableOrDemoteCurrentAdministrator() {
        assertThatThrownBy(() -> users.edit(admin, admin.id(), admin.name(), admin.email(), "ADMIN", false)).isInstanceOf(Problem.class);
        assertThatThrownBy(() -> users.edit(admin, admin.id(), admin.name(), admin.email(), "EMPLOYEE", true)).isInstanceOf(Problem.class);
    }
    @Test void cannotTakeAnotherAccountsEmail() {
        users.create(admin, "Alex", "alex@example.test", "EMPLOYEE", "Test-password-123");
        var alex = users.list().stream().filter(a -> a.name().equals("Alex")).findFirst().orElseThrow();
        assertThatThrownBy(() -> users.edit(admin, alex.id(), "Alex", "admin@example.test", "EMPLOYEE", true))
                .isInstanceOf(ValidationProblem.class);
        users.edit(admin, alex.id(), "Alex Murphy", "ALEX@example.test", "EMPLOYEE", true);
        assertThat(users.find(alex.id()).name()).isEqualTo("Alex Murphy");
    }
    @Test void onlyAdministratorsManageAccounts() {
        users.create(admin, "Tech", "tech@example.test", "TECHNICIAN", "Test-password-123");
        Account tech = users.technicians().stream().filter(a -> !a.isAdmin()).findFirst().orElseThrow();
        assertThatThrownBy(() -> users.create(tech, "X", "x@example.test", "ADMIN", "Test-password-123"))
                .isInstanceOf(Problem.class).extracting(e -> ((Problem) e).status()).isEqualTo(403);
        assertThatThrownBy(() -> users.edit(tech, tech.id(), "Tech", "tech@example.test", "ADMIN", true))
                .isInstanceOf(Problem.class).extracting(e -> ((Problem) e).status()).isEqualTo(403);
    }
    @Test void staffNeedTheirOpenWorkReassignedBeforeBeingDisabled() {
        users.create(admin, "Tech", "tech@example.test", "TECHNICIAN", "Test-password-123");
        Account tech = users.technicians().stream().filter(a -> !a.isAdmin()).findFirst().orElseThrow();
        long id = tickets.create(admin, "Test", "Details", "Other", "Low");
        tickets.update(admin, id, 0, "Assigned", "Low", tech.id(), "");
        assertThatThrownBy(() -> users.edit(admin, tech.id(), tech.name(), tech.email(), tech.role(), false))
                .isInstanceOf(ValidationProblem.class)
                .satisfies(e -> assertThat(((ValidationProblem) e).fields().get("enabled")).contains("Reassign"));
        tickets.update(admin, id, 1, "Assigned", "Low", admin.id(), "");
        users.edit(admin, tech.id(), tech.name(), tech.email(), tech.role(), false);
        assertThat(users.find(tech.id()).enabled()).isFalse();
    }
}
