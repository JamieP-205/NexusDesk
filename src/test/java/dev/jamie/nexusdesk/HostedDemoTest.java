package dev.jamie.nexusdesk;

import dev.jamie.nexusdesk.hosted.SandboxRegistry;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The public browser demo: one private sandbox per visitor, with limits. */
@SpringBootTest(properties = {"nexusdesk.hosted.max-sandboxes=6", "nexusdesk.hosted.trusted-proxy-hops=1", "nexusdesk.hosted.creations-per-minute=100",
        "nexusdesk.hosted.max-tickets=27", "nexusdesk.hosted.max-rows=400", "server.servlet.session.cookie.secure=false"})
@ActiveProfiles("hosted")
@AutoConfigureMockMvc
class HostedDemoTest {
    @Autowired MockMvc mvc;
    @Autowired SandboxRegistry registry;
    @Autowired HttpSessionListener sessionCleanup;

    @org.junit.jupiter.api.AfterEach void clear() { registry.removeAll(); }

    static int visitor;

    MockHttpSession start(String role) throws Exception {
        var session = new MockHttpSession();
        MvcResult result = mvc.perform(post("/demo/start").param("role", role).session(session).with(csrf())
                        .header("X-Forwarded-For", "10.0.0." + (++visitor % 250)))
                .andExpect(status().is3xxRedirection()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
    String page(MockHttpSession session, String path) throws Exception {
        return mvc.perform(get(path).session(session)).andReturn().getResponse().getContentAsString();
    }
    String search(MockHttpSession session, String q) throws Exception {
        return mvc.perform(get("/tickets").param("q", q).session(session)).andReturn().getResponse().getContentAsString();
    }
    long createTicket(MockHttpSession session, String title) throws Exception {
        String location = mvc.perform(post("/tickets").session(session).with(csrf()).param("title", title)
                        .param("description", "Details").param("category", "Other").param("priority", "Low"))
                .andReturn().getResponse().getRedirectedUrl();
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    @Test void theSignInPageOffersOneClickRolesAndNoSharedPassword() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Try the demo")))
                .andExpect(content().string(containsString("action=\"/demo/start\"")));
        // Without a sandbox there's nothing to sign in to.
        mvc.perform(post("/login").param("username", "admin@example.test").param("password", "Demo-password-123").with(csrf()))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test void eachVisitorGetsTheirOwnData() throws Exception {
        var alice = start("employee");
        var bob = start("technician");
        createTicket(alice, "Visible only to Alice in her demo");
        assertThat(search(alice, "visible only")).contains("Visible only to Alice");
        assertThat(search(bob, "visible only")).doesNotContain("Visible only to Alice");
    }

    @Test void switchingRolesStaysInTheSameSandboxAndKeepsPermissions() throws Exception {
        var visitor = start("employee");
        long id = createTicket(visitor, "Raised as the employee");
        mvc.perform(get("/users").session(visitor)).andExpect(status().isForbidden());
        var tech = (MockHttpSession) mvc.perform(post("/demo/switch").param("role", "technician").session(visitor).with(csrf()))
                .andExpect(redirectedUrl("/")).andReturn().getRequest().getSession(false);
        assertThat(page(tech, "/tickets/" + id)).contains("Raised as the employee");
        mvc.perform(get("/users").session(tech)).andExpect(status().isForbidden());
        var admin = (MockHttpSession) mvc.perform(post("/demo/switch").param("role", "admin").session(tech).with(csrf()))
                .andReturn().getRequest().getSession(false);
        mvc.perform(get("/users").session(admin)).andExpect(status().isOk());
    }

    @Test void resetRestoresTheOriginalData() throws Exception {
        var visitor = start("employee");
        createTicket(visitor, "Gone after reset");
        assertThat(search(visitor, "gone after")).contains("Gone after reset");
        var after = (MockHttpSession) mvc.perform(post("/demo/reset").param("role", "employee").session(visitor).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn().getRequest().getSession(false);
        assertThat(search(after, "gone after")).doesNotContain("Gone after reset").contains("No tickets match");
    }

    @Test void anExpiredSandboxEndsTheSessionCleanly() throws Exception {
        var visitor = start("employee");
        registry.remove((String) visitor.getAttribute("nexusdesk.sandbox"));
        mvc.perform(get("/tickets").session(visitor)).andExpect(redirectedUrl("/login?ended"));
    }

    @Test void endingASessionDeletesItsSandbox() throws Exception {
        var visitor = start("employee");
        int before = registry.size();
        sessionCleanup.sessionDestroyed(new HttpSessionEvent(visitor));
        assertThat(registry.size()).isEqualTo(before - 1);
    }

    @Test void eachSandboxHasARowLimit() throws Exception {
        var visitor = start("employee");
        createTicket(visitor, "Ticket 27, the last one allowed");
        mvc.perform(post("/tickets").session(visitor).with(csrf()).param("title", "One too many")
                        .param("description", "Details").param("category", "Other").param("priority", "Low"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("reached its limit of 27 tickets")));
    }

    @Test void historyGrowthIsCappedToo() throws Exception {
        var visitor = start("technician");
        // Flip one ticket between In Progress and Waiting until the sandbox's total size limit is hit.
        String html = search(visitor, "Wi-Fi drops");
        var m = Pattern.compile("href=\"/tickets/(\\d+)\"").matcher(html);
        assertThat(m.find()).isTrue();
        String id = m.group(1);
        int status = 0;
        String body = "";
        for (int i = 0; i < 400 && status != 400; i++) {
            String page = page(visitor, "/tickets/" + id);
            var v = Pattern.compile("name=\"version\"\\s*value=\"(\\d+)\"").matcher(page);
            assertThat(v.find()).isTrue();
            var a = Pattern.compile("value=\"(\\d+)\"\\s*selected=\"selected\"").matcher(page.substring(page.indexOf("id=\"assignee\"")));
            assertThat(a.find()).isTrue();
            var response = mvc.perform(post("/tickets/" + id).session(visitor).with(csrf()).param("version", v.group(1))
                    .param("status", i % 2 == 0 ? "Waiting" : "In Progress").param("priority", "High").param("assignee", a.group(1)))
                    .andReturn().getResponse();
            status = response.getStatus();
            body = response.getContentAsString();
        }
        assertThat(status).isEqualTo(400);
        assertThat(body).contains("size limit");
        assertThat(page(visitor, "/tickets/" + id)).doesNotContain("size limit");
        mvc.perform(post("/tickets/" + id + "/comments").session(visitor).with(csrf()).param("body", "More"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("size limit")));
    }

    @Test void disablingADemoAccountOnlyAffectsThatVisitor() throws Exception {
        var visitor = start("admin");
        var otherVisitor = start("employee");
        // The administrator disables the employee account in their own demo.
        String edit = page(visitor, "/users");
        var matcher = Pattern.compile("href=\"/users/(\\d+)\"[^>]*aria-label=\"Edit Alex Murphy\"").matcher(edit);
        assertThat(matcher.find()).isTrue();
        mvc.perform(post("/users/" + matcher.group(1)).session(visitor).with(csrf()).param("name", "Alex Murphy")
                .param("email", "employee@example.test").param("role", "EMPLOYEE")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/demo/switch").param("role", "employee").session(visitor).with(csrf()))
                .andExpect(flash().attribute("error", containsString("disabled")));
        // Someone else's demo is untouched.
        assertThat(page(otherVisitor, "/tickets")).contains("Alex Murphy");
    }

    @Test void passwordFailuresOnlyThrottleTheirOwnSandbox() throws Exception {
        var attacker = start("employee");
        for (int i = 0; i < 6; i++) {
            mvc.perform(post("/login").session(attacker).with(csrf()).param("username", "admin@example.test").param("password", "wrong"));
        }
        mvc.perform(post("/login").session(attacker).with(csrf()).param("username", "admin@example.test").param("password", "Demo-password-123"))
                .andExpect(header().string("Location", startsWith("/login?locked")));
        var someoneElse = start("employee");
        mvc.perform(post("/login").session(someoneElse).with(csrf()).param("username", "admin@example.test").param("password", "Demo-password-123"))
                .andExpect(redirectedUrl("/"));
    }

    @Test void aFullDemoOnlyReplacesIdleSandboxes() throws Exception {
        for (int i = 0; i < 6; i++) startFrom("10.9.0." + i);
        // Everyone is active, so a newcomer is asked to wait rather than pushing someone out.
        mvc.perform(post("/demo/start").param("role", "employee").session(new MockHttpSession()).with(csrf())
                        .header("X-Forwarded-For", "10.9.1.1"))
                .andExpect(flash().attribute("demoError", containsString("full")));
    }

    @Test void oneAddressCantTakeEverySandbox() throws Exception {
        for (int i = 0; i < 6; i++) startFrom("10.8.0.1");
        mvc.perform(post("/demo/start").param("role", "employee").session(new MockHttpSession()).with(csrf())
                        .header("X-Forwarded-For", "10.0.0.2, 10.8.0.1"))
                .andExpect(flash().attribute("demoError", containsString("several demos")));
    }

    @Test void aForgedEarlierAddressCantUseUpSomeoneElsesQuota() throws Exception {
        for (int i = 0; i < 4; i++) startFrom("10.6.0.1");
        registry.removeAll();
        // Someone else claims 10.6.0.1 at the front of the header; the proxy-added address is their own.
        startFrom("10.6.0.1, 10.6.0.99");
        startFrom("10.6.0.1, 10.6.0.98");
        registry.removeAll();
        // Had the forged entries counted, this would be 10.6.0.1's seventh start this minute and refused.
        mvc.perform(post("/demo/start").param("role", "employee").session(new MockHttpSession()).with(csrf())
                        .header("X-Forwarded-For", "10.6.0.1"))
                .andExpect(redirectedUrl("/tickets"));
    }

    MockHttpSession startFrom(String address) throws Exception {
        return (MockHttpSession) mvc.perform(post("/demo/start").param("role", "employee").session(new MockHttpSession())
                .with(csrf()).header("X-Forwarded-For", address)).andReturn().getRequest().getSession(false);
    }

}
