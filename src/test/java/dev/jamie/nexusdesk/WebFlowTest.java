package dev.jamie.nexusdesk;

import dev.jamie.nexusdesk.model.Account;
import dev.jamie.nexusdesk.service.*;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Drives the real controllers, templates and security filters through HTTP requests. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:webflow;DB_CLOSE_DELAY=-1", "NEXUSDESK_ADMIN_PASSWORD=Test-password-123"})
@AutoConfigureMockMvc
@Transactional
class WebFlowTest {
    static final String PASSWORD = "Test-password-123";
    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired TicketService tickets;
    @Autowired AssetService assets;
    @Autowired JdbcTemplate db;
    Account admin, tech, alex, sam;

    @BeforeEach void setup() {
        admin = users.list().stream().filter(Account::isAdmin).findFirst().orElseThrow();
        users.create(admin, "Morgan Reid", "tech@example.test", "TECHNICIAN", PASSWORD);
        users.create(admin, "Alex Murphy", "alex@example.test", "EMPLOYEE", PASSWORD);
        users.create(admin, "Sam Okafor", "sam@example.test", "EMPLOYEE", PASSWORD);
        tech = byEmail("tech@example.test"); alex = byEmail("alex@example.test"); sam = byEmail("sam@example.test");
    }
    Account byEmail(String email) { return users.list().stream().filter(a -> a.email().equals(email)).findFirst().orElseThrow(); }
    static RequestPostProcessor as(Account a) { return user(a.email()).roles(a.role()); }
    static RequestPostProcessor from(String address) { return request -> { request.setRemoteAddr(address); return request; }; }
    /** Thymeleaf spreads attributes over several lines, so match "selected" near the option text or value. */
    static org.hamcrest.Matcher<String> selectedOption(String marker) {
        String m = java.util.regex.Pattern.quote(marker);
        return matchesPattern(java.util.regex.Pattern.compile("(?s).*(" + m + "\\s*selected=\"selected\"|selected=\"selected\"\\s*" + m + ").*"));
    }
    static MockHttpServletRequestBuilder login(String email, String password) {
        return post("/login").param("username", email).param("password", password).with(csrf());
    }

    // --- Permissions: the server refuses, whatever the page shows ---

    @Test void employeesCantUseStaffOrAdminActions() throws Exception {
        long mine = tickets.create(alex, "Mine", "Details", "Other", "Low");
        long asset = assets.create(admin, "Laptop", "Dell", "X", "WF-1", LocalDate.now());
        mvc.perform(get("/").with(as(alex))).andExpect(redirectedUrl("/tickets"));
        mvc.perform(post("/tickets/" + mine).with(as(alex)).with(csrf()).param("version", "0")
                .param("status", "Assigned").param("priority", "Low").param("assignee", String.valueOf(tech.id())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/assets/new").with(as(alex))).andExpect(status().isForbidden());
        mvc.perform(post("/assets").with(as(alex)).with(csrf()).param("type", "Laptop").param("manufacturer", "A")
                .param("model", "B").param("serial", "C").param("purchaseDate", "2024-01-01")).andExpect(status().isForbidden());
        mvc.perform(post("/assets/" + asset + "/assign").with(as(alex)).with(csrf()).param("owner", String.valueOf(alex.id())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/assets/" + asset).with(as(alex))).andExpect(status().isNotFound());
        mvc.perform(get("/users").with(as(alex))).andExpect(status().isForbidden());
        mvc.perform(post("/users").with(as(alex)).with(csrf()).param("name", "X").param("email", "x@example.test")
                .param("role", "ADMIN").param("password", PASSWORD)).andExpect(status().isForbidden());
        assertThat(tickets.get(admin, mine).status()).isEqualTo("Open");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM users WHERE email='x@example.test'", Integer.class)).isZero();
    }
    @Test void employeesOnlySeeTheirOwnTickets() throws Exception {
        long alexTicket = tickets.create(alex, "Alex's laptop", "Details", "Hardware", "Low");
        tickets.create(sam, "Sam's private printer problem", "Details", "Printer", "Low");
        mvc.perform(get("/tickets/" + alexTicket).with(as(sam))).andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Alex's laptop"))));
        mvc.perform(post("/tickets/" + alexTicket + "/comments").with(as(sam)).with(csrf()).param("body", "Hi"))
                .andExpect(status().isNotFound());
        // Sending someone else's ID as a filter doesn't widen the list.
        mvc.perform(get("/tickets").param("creator", String.valueOf(alex.id())).with(as(sam)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sam&#39;s private printer problem")))
                .andExpect(content().string(not(containsString("Alex&#39;s laptop"))));
    }
    @Test void techniciansCantManageAccountsOrEquipment() throws Exception {
        mvc.perform(get("/users").with(as(tech))).andExpect(status().isForbidden());
        mvc.perform(get("/assets/new").with(as(tech))).andExpect(status().isForbidden());
        mvc.perform(get("/").with(as(tech))).andExpect(status().isOk());
    }
    @Test void disabledOrDemotedAccountsLoseTheirSession() throws Exception {
        var session = (MockHttpSession) mvc.perform(login("tech@example.test", PASSWORD).with(from("10.1.0.1")))
                .andExpect(authenticated()).andReturn().getRequest().getSession();
        mvc.perform(get("/").session(session)).andExpect(status().isOk());
        db.update("UPDATE users SET role='EMPLOYEE' WHERE id=?", tech.id());
        mvc.perform(get("/").session(session)).andExpect(redirectedUrl("/login?expired"));
        db.update("UPDATE users SET role='TECHNICIAN', enabled=FALSE WHERE id=?", tech.id());
        mvc.perform(login("tech@example.test", PASSWORD).with(from("10.1.0.1"))).andExpect(unauthenticated());
    }
    @Test void signInAndPostsNeedCsrfTokens() throws Exception {
        mvc.perform(post("/login").param("username", "alex@example.test").param("password", PASSWORD))
                .andExpect(status().isForbidden()).andExpect(unauthenticated());
        mvc.perform(post("/logout").with(as(alex))).andExpect(status().isForbidden());
    }
    @Test void sessionsUseCookiesNotUrls() throws Exception {
        mvc.perform(get("/login")).andExpect(content().string(not(containsString("jsessionid"))));
        var result = mvc.perform(login("alex@example.test", PASSWORD).with(from("10.1.0.9"))).andReturn();
        // Spring Security issues a fresh session at sign-in, so a planted session ID is useless.
        var before = new MockHttpSession();
        String plantedId = before.getId();
        var after = mvc.perform(login("alex@example.test", PASSWORD).session(before).with(from("10.1.0.9")))
                .andReturn().getRequest().getSession(false);
        assertThat(after).isNotNull();
        assertThat(after.getId()).isNotEqualTo(plantedId);
        assertThat(result.getResponse().getHeader("Content-Security-Policy")).contains("script-src 'self'");
    }
    @Test void errorPagesDontLeakInternals() throws Exception {
        mvc.perform(get("/tickets/abc").with(as(admin))).andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("Exception"))));
        mvc.perform(post("/tickets/1").with(as(admin)).with(csrf()).param("version", "x"))
                .andExpect(status().isBadRequest()).andExpect(content().string(not(containsString("NumberFormat"))));
        mvc.perform(get("/no-such-page").with(as(admin))).andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("trace"))));
    }

    // --- Sign-in throttling ---

    @Test void repeatedFailuresBlockEvenTheCorrectPassword() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(login("alex@example.test", "wrong-" + i).with(from("10.2.0.1"))).andExpect(redirectedUrl("/login?error"));
        }
        mvc.perform(login("alex@example.test", PASSWORD).with(from("10.2.0.1")))
                .andExpect(redirectedUrl("/login?locked=15")).andExpect(unauthenticated());
        mvc.perform(get("/login").param("locked", "15")).andExpect(content().string(containsString("wait 15 minutes")));
        // The same person from another address isn't locked out by someone else's guessing.
        mvc.perform(login("alex@example.test", PASSWORD).with(from("10.2.0.2"))).andExpect(authenticated());
    }
    @Test void oneAddressCantGuessAcrossManyAccounts() throws Exception {
        for (int i = 0; i < 20; i++) {
            mvc.perform(login("person" + i + "@example.test", "wrong").with(from("10.3.0.1"))).andExpect(redirectedUrl("/login?error"));
        }
        mvc.perform(login("sam@example.test", PASSWORD).with(from("10.3.0.1"))).andExpect(unauthenticated());
    }
    @Test void lockoutMessageOnlyEchoesANumber() throws Exception {
        mvc.perform(get("/login").param("locked", "<b>soon</b>"))
                .andExpect(content().string(containsString("wait 15 minutes")))
                .andExpect(content().string(not(containsString("soon"))));
    }

    // --- Password storage ---

    @Test @SuppressWarnings("deprecation")
    void oldSha1HashesStillSignInAndAreUpgraded() throws Exception {
        // Exactly what version 0.1.0 stored, tagged the way migration V3 tags it.
        String old = new Pbkdf2PasswordEncoder("", 16, 600_000, 256).encode("Old-password-456");
        db.update("UPDATE users SET password_hash=? WHERE id=?", "{pbkdf2-sha1}" + old, sam.id());
        mvc.perform(login("sam@example.test", "wrong-password").with(from("10.4.0.1"))).andExpect(unauthenticated());
        assertThat(hash(sam)).startsWith("{pbkdf2-sha1}");
        mvc.perform(login("sam@example.test", "Old-password-456").with(from("10.4.0.1"))).andExpect(authenticated());
        assertThat(hash(sam)).startsWith("{pbkdf2-sha256}").doesNotContain(old);
        mvc.perform(login("sam@example.test", "Old-password-456").with(from("10.4.0.1"))).andExpect(authenticated());
    }
    @Test void anUntaggedHashFailsClosed() throws Exception {
        db.update("UPDATE users SET password_hash='abc123' WHERE id=?", sam.id());
        mvc.perform(login("sam@example.test", "abc123").with(from("10.4.0.2"))).andExpect(unauthenticated());
    }
    String hash(Account a) { return db.queryForObject("SELECT password_hash FROM users WHERE id=?", String.class, a.id()); }

    // --- Forms keep what people typed and explain what to fix ---

    @Test void ticketFormShowsFieldErrorsAndKeepsInput() throws Exception {
        mvc.perform(post("/tickets").with(as(alex)).with(csrf()).param("title", "   ")
                        .param("description", "My <b>monitor</b> flickers").param("category", "Hardware").param("priority", "High"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Title is required.")))
                .andExpect(content().string(matchesPattern(java.util.regex.Pattern.compile("(?s).*id=\"title-error\"\\s*>Title is required\\.</p>.*"))))
                .andExpect(content().string(containsString("aria-invalid=\"true\"")))
                .andExpect(content().string(containsString("My &lt;b&gt;monitor&lt;/b&gt; flickers")))
                .andExpect(content().string(selectedOption(">High<")));
        assertThat(tickets.search(admin, TicketFilter.none(), 1, 20).total()).isZero();
    }
    @Test void openTicketWithATechnicianIsExplainedAndTheChoiceKept() throws Exception {
        long id = tickets.create(alex, "Laptop", "Details", "Hardware", "Low");
        mvc.perform(post("/tickets/" + id).with(as(tech)).with(csrf()).param("version", "0").param("status", "Open")
                        .param("priority", "Low").param("assignee", String.valueOf(tech.id())))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("change the status to Assigned")))
                .andExpect(content().string(selectedOption("value=\"" + tech.id() + "\"")))
                .andExpect(content().string(not(containsString("Saved:"))));
        assertThat(tickets.get(admin, id).assigneeId()).isNull();
        mvc.perform(post("/tickets/" + id).with(as(tech)).with(csrf()).param("version", "0").param("status", "Assigned")
                        .param("priority", "Low").param("assignee", String.valueOf(tech.id())))
                .andExpect(redirectedUrl("/tickets/" + id))
                .andExpect(flash().attribute("success", "Saved: status Open → Assigned; technician Unassigned → Morgan Reid."));
        mvc.perform(post("/tickets/" + id).with(as(tech)).with(csrf()).param("version", "1").param("status", "Assigned")
                        .param("priority", "Low").param("assignee", String.valueOf(tech.id())))
                .andExpect(flash().attribute("info", "Nothing changed, so there was nothing to save."));
    }
    @Test void staleTicketEditShowsTheConflictAndKeepsTheDraft() throws Exception {
        long id = tickets.create(alex, "Laptop", "Details", "Hardware", "Low");
        tickets.comment(alex, id, "Extra detail from Alex");
        mvc.perform(post("/tickets/" + id).with(as(tech)).with(csrf()).param("version", "0").param("status", "Assigned")
                        .param("priority", "Critical").param("assignee", String.valueOf(tech.id())))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("Nothing was saved")))
                .andExpect(content().string(containsString("Extra detail from Alex")))
                .andExpect(content().string(selectedOption(">Critical<")))
                // The form now carries the latest version, so a deliberate re-save can succeed.
                .andExpect(content().string(matchesPattern(java.util.regex.Pattern.compile("(?s).*name=\"version\"\\s*value=\"1\".*"))));
        assertThat(tickets.get(admin, id).priority()).isEqualTo("Low");
    }
    @Test void aConflictKeepsOnlyMyChangesNotTheStaleValues() throws Exception {
        long id = tickets.create(alex, "Laptop", "Details", "Hardware", "Low");
        tickets.update(tech, id, 0, "Assigned", "Low", tech.id(), "");
        // Someone else raises the priority while I'm looking at version 1...
        tickets.update(admin, id, 1, "Assigned", "Critical", tech.id(), "");
        // ...and I only change the status, from the page I opened before their save.
        mvc.perform(post("/tickets/" + id).with(as(tech)).with(csrf()).param("version", "1")
                        .param("seenStatus", "Assigned").param("seenPriority", "Low").param("seenAssignee", String.valueOf(tech.id()))
                        .param("status", "In Progress").param("priority", "Low").param("assignee", String.valueOf(tech.id())))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("Your edits (status In Progress)")))
                .andExpect(content().string(selectedOption(">Critical<")))
                .andExpect(content().string(selectedOption("value=\"In Progress\"")));
        assertThat(tickets.get(admin, id).status()).isEqualTo("Assigned");
    }
    @Test void accountFormKeepsInputButNeverEchoesThePassword() throws Exception {
        mvc.perform(post("/users").with(as(admin)).with(csrf()).param("name", "Priya Shah").param("email", "alex@example.test")
                        .param("role", "TECHNICIAN").param("password", "Secret-password-999"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Another account already uses this email address.")))
                // The message also appears beside the field, linked to the input for screen readers.
                .andExpect(content().string(matchesPattern(java.util.regex.Pattern.compile(
                        "(?s).*<p\\s+class=\"field-error\"\\s+id=\"email-error\"\\s*>Another account already uses this email address\\.</p>.*"))))
                .andExpect(content().string(matchesPattern(java.util.regex.Pattern.compile("(?s).*aria-describedby=\"email-error\".*"))))
                .andExpect(content().string(containsString("value=\"Priya Shah\"")))
                .andExpect(content().string(selectedOption("value=\"TECHNICIAN\"")))
                .andExpect(content().string(not(containsString("Secret-password-999"))));
    }
    @Test void equipmentFormReportsEveryProblemAtOnce() throws Exception {
        mvc.perform(post("/assets").with(as(admin)).with(csrf()).param("type", "Laptop").param("manufacturer", "Lenovo")
                        .param("model", " ").param("serial", "S-1").param("purchaseDate", LocalDate.now().plusDays(3).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Model is required.")))
                .andExpect(content().string(containsString("Use a purchase date today or earlier.")))
                .andExpect(content().string(containsString("value=\"Lenovo\"")));
    }
    @Test void assigningWithoutChoosingAPersonExplainsWhy() throws Exception {
        long asset = assets.create(admin, "Laptop", "Dell", "X", "WF-2", LocalDate.now());
        mvc.perform(post("/assets/" + asset + "/assign").with(as(admin)).with(csrf()).param("owner", ""))
                .andExpect(redirectedUrl("/assets/" + asset))
                .andExpect(flash().attributeExists("errors"));
        assertThat(assets.get(admin, asset).ownerId()).isNull();
    }

    // --- Pagination keeps the search ---

    @Test void pageLinksKeepFiltersAndSearch() throws Exception {
        for (int i = 0; i < 23; i++) tickets.create(alex, "Printer jam " + i, "Details", "Printer", "High");
        tickets.create(alex, "Unrelated", "Details", "Other", "Low");
        mvc.perform(get("/tickets").param("q", "printer & jam").with(as(admin)))
                .andExpect(content().string(containsString("No tickets match these filters")));
        mvc.perform(get("/tickets").param("q", "jam").param("category", "Printer").param("priority", "High").with(as(admin)))
                .andExpect(content().string(containsString("23 tickets")))
                .andExpect(content().string(containsString("Showing <span>1–20</span>")))
                .andExpect(content().string(containsString("href=\"/tickets?q=jam&amp;priority=High&amp;category=Printer&amp;page=2\"")));
        mvc.perform(get("/tickets").param("q", "jam").param("category", "Printer").param("priority", "High").param("page", "2").with(as(admin)))
                .andExpect(content().string(containsString("Showing <span>21–23</span>")))
                .andExpect(content().string(containsString("value=\"jam\"")))
                .andExpect(content().string(containsString("aria-current=\"page\"")));
        mvc.perform(get("/tickets").param("page", "nonsense").with(as(admin))).andExpect(status().isOk());
        mvc.perform(get("/tickets").param("from", "2026-05-02").param("to", "2026-05-01").with(as(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("The end date must be on or after the start date.")));
    }
    @Test void emptyStatesMatchTheSituation() throws Exception {
        mvc.perform(get("/tickets").with(as(sam))).andExpect(content().string(containsString("You have no tickets yet")));
        mvc.perform(get("/tickets").with(as(admin))).andExpect(content().string(containsString("The queue is empty")));
        mvc.perform(get("/assets").with(as(sam))).andExpect(content().string(containsString("Equipment will appear here once it is assigned.")));
    }
}
