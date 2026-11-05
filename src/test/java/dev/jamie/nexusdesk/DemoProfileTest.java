package dev.jamie.nexusdesk;

import dev.jamie.nexusdesk.config.Bootstrap;
import dev.jamie.nexusdesk.config.DatabaseMode;
import dev.jamie.nexusdesk.config.DemoData;
import dev.jamie.nexusdesk.service.TicketFilter;
import dev.jamie.nexusdesk.service.TicketService;
import dev.jamie.nexusdesk.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:demo-profile;DB_CLOSE_DELAY=-1")
@ActiveProfiles("demo")
@AutoConfigureMockMvc
class DemoProfileTest {
    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate db;
    @Autowired DatabaseMode mode;
    @Autowired UserService users;
    @Autowired TicketService tickets;
    @Autowired MockMvc mvc;

    @Test void seedsFictionalDataMarksTheDatabaseAndSkipsNormalBootstrap() throws Exception {
        assertThat(context.getBeansOfType(Bootstrap.class)).isEmpty();
        assertThat(mode.isMarkedDemo()).isTrue();
        assertThat(users.list()).extracting(a -> a.email()).allMatch(e -> e.endsWith("@example.test"));
        var admin = users.list().stream().filter(a -> a.isAdmin()).findFirst().orElseThrow();
        // Enough tickets for a second page, and a spread of statuses.
        assertThat(tickets.search(admin, TicketFilter.none(), 1, 20).totalPages()).isGreaterThan(1);
        assertThat(db.queryForList("SELECT DISTINCT status FROM tickets", String.class))
                .containsExactlyInAnyOrder("Open", "Assigned", "In Progress", "Waiting", "Resolved", "Closed");
        // Every non-Open ticket has a technician, every Open one has none.
        assertThat(db.queryForObject("SELECT COUNT(*) FROM tickets WHERE (status='Open') <> (assignee_id IS NULL)", Integer.class)).isZero();
        mvc.perform(post("/login").param("username", "employee@example.test").param("password", DemoData.PUBLIC_PASSWORD).with(csrf()))
                .andExpect(authenticated());
        mvc.perform(get("/login")).andExpect(content().string(containsString("Demo accounts")));
    }

    @Test void normalStartupRefusesADemoDatabase() {
        var bootstrap = new Bootstrap(users, mode, new MockEnvironment().withProperty("NEXUSDESK_ADMIN_PASSWORD", "Another-password-1"));
        assertThatThrownBy(() -> bootstrap.run(null)).isInstanceOf(IllegalStateException.class).hasMessageContaining("demo profile");
    }

    @Test void demoSeederRefusesARealDatabase() {
        db.update("DELETE FROM app_metadata");
        db.update("UPDATE assets SET serial_number='REAL-1001' WHERE serial_number='DEMO-1001'");
        try {
            var seeder = context.getBean(DemoData.class);
            assertThatThrownBy(() -> seeder.run(null)).isInstanceOf(IllegalStateException.class).hasMessageContaining("real data");
        } finally {
            db.update("UPDATE assets SET serial_number='DEMO-1001' WHERE serial_number='REAL-1001'");
            mode.markDemo();
        }
    }
}
