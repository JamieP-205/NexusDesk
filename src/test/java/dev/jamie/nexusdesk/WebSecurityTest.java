package dev.jamie.nexusdesk;

import dev.jamie.nexusdesk.model.Account;
import dev.jamie.nexusdesk.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:web;DB_CLOSE_DELAY=-1", "NEXUSDESK_ADMIN_PASSWORD=Test-password-123"})
@AutoConfigureMockMvc
@Transactional
class WebSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired TicketService tickets;
    @Autowired AssetService assets;
    @Autowired JdbcTemplate db;
    Account admin;
    Account employee;
    @BeforeEach void setup() {
        admin = users.list().stream().filter(Account::isAdmin).findFirst().orElseThrow();
        users.create(admin, "Employee", "employee@example.test", "EMPLOYEE", "Test-password-123");
        employee = users.list().stream().filter(a -> !a.isStaff()).findFirst().orElseThrow();
    }
    @Test void rejectsBadLoginAndAcceptsValidPassword() throws Exception {
        mvc.perform(formLogin().user("admin@example.test").password("wrong")).andExpect(unauthenticated());
        mvc.perform(formLogin().user("admin@example.test").password("Test-password-123")).andExpect(authenticated());
    }
    @Test void requiresLoginAndCsrf() throws Exception {
        mvc.perform(get("/tickets")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/tickets").with(user("employee@example.test").roles("EMPLOYEE"))
                .param("title", "Test").param("description", "Description").param("category", "Other").param("priority", "Low"))
                .andExpect(status().isForbidden());
    }
    @Test void employeeCannotOpenAdminOrAnotherPersonsTicket() throws Exception {
        long id = tickets.create(admin, "Private", "Private details", "Account", "High");
        mvc.perform(get("/users").with(user("employee@example.test").roles("EMPLOYEE"))).andExpect(status().isForbidden());
        mvc.perform(get("/tickets/" + id).with(user("employee@example.test").roles("EMPLOYEE"))).andExpect(status().isNotFound());
    }
    @Test void disabledSessionIsRejectedOnItsNextRequest() throws Exception {
        db.update("UPDATE users SET enabled=FALSE WHERE id=?", employee.id());
        mvc.perform(get("/tickets").with(user("employee@example.test").roles("EMPLOYEE")))
                .andExpect(redirectedUrl("/login?expired"));
    }
    @Test void allMainPagesRenderAndUserContentIsEscaped() throws Exception {
        long id = tickets.create(employee, "<script>alert(1)</script>", "Description", "Other", "Low");
        assets.create(admin, "Laptop", "Dell", "Test", "WEB-1", java.time.LocalDate.now());
        long asset = assets.list(admin, null).get(0).id();
        for (String route : new String[]{"/", "/tickets", "/tickets/new", "/tickets/" + id, "/assets", "/assets/new", "/assets/" + asset, "/users", "/users/" + employee.id()}) {
            mvc.perform(get(route).with(user("admin@example.test").roles("ADMIN"))).andExpect(status().isOk());
        }
        mvc.perform(get("/tickets/" + id).with(user("employee@example.test").roles("EMPLOYEE")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>alert"))));
    }
    @Test void invalidIdsReturnSafeResponses() throws Exception {
        mvc.perform(get("/tickets/not-a-number").with(user("admin@example.test").roles("ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(get("/assets/9999999").with(user("admin@example.test").roles("ADMIN"))).andExpect(status().isNotFound());
    }
}
