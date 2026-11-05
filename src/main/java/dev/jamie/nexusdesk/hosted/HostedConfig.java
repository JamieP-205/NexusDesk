package dev.jamie.nexusdesk.hosted;

import dev.jamie.nexusdesk.config.ThrottleKey;
import dev.jamie.nexusdesk.service.RecordLimits;
import dev.jamie.nexusdesk.service.ValidationProblem;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import java.util.Map;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The public browser demo ("hosted" profile). Each visitor gets a private in-memory copy of the
 * fictional data. Nothing is written to disk, nothing is shared between visitors, and the app has
 * no email or other outside services that a visitor could trigger.
 */
@Configuration
@Profile("hosted")
@EnableScheduling
public class HostedConfig {

    /** All queries go to the visitor's sandbox; the empty "lobby" only serves the sign-in page. */
    @Bean
    @Primary
    DataSource dataSource() {
        var lobby = new JdbcDataSource();
        lobby.setURL("jdbc:h2:mem:lobby;DB_CLOSE_DELAY=-1");
        lobby.setUser("sa");
        return new SandboxRoutingDataSource(lobby);
    }

    @Bean
    FilterRegistrationBean<SandboxFilter> sandboxFilter(SandboxRegistry registry) {
        var bean = new FilterRegistrationBean<>(new SandboxFilter(registry));
        // Before Spring Security, which reads accounts from the database.
        bean.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER - 10);
        return bean;
    }

    /** Signing out, or a session timing out, deletes that visitor's sandbox straight away. */
    @Bean
    HttpSessionListener sandboxCleanup(SandboxRegistry registry) {
        return new HttpSessionListener() {
            @Override public void sessionDestroyed(HttpSessionEvent event) {
                registry.remove((String) event.getSession().getAttribute(SandboxFilter.SESSION_KEY));
            }
        };
    }

    @Bean
    ApplicationRunner buildDemoTemplate(SandboxRegistry registry) {
        return args -> registry.refreshTemplate();
    }

    /** Sign-in failures count against the visitor's sandbox, not the proxy address everyone shares. */
    @Bean
    ThrottleKey sandboxThrottleKey() {
        return address -> {
            Sandbox sandbox = SandboxContext.current();
            return sandbox == null ? "lobby:" + address : "sandbox:" + sandbox.id();
        };
    }

    @Bean
    RecordLimits sandboxLimits(JdbcTemplate db,
                               @Value("${nexusdesk.hosted.max-tickets:150}") int tickets,
                               @Value("${nexusdesk.hosted.max-comments:400}") int comments,
                               @Value("${nexusdesk.hosted.max-users:40}") int users,
                               @Value("${nexusdesk.hosted.max-assets:60}") int assets) {
        Map<String, Integer> caps = Map.of("tickets", tickets, "comments", comments, "users", users, "assets", assets);
        Map<String, String> names = Map.of("tickets", "tickets", "comments", "comments", "users", "accounts", "assets", "pieces of equipment");
        return table -> {
            Integer cap = caps.get(table);
            if (cap == null || SandboxContext.current() == null) return;
            // The table name comes only from the fixed map above.
            Integer count = db.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
            if (count != null && count >= cap) {
                throw ValidationProblem.of("limit", "This demo has reached its limit of " + cap + " " + names.get(table)
                        + ". Use Reset demo in the sidebar to start again with fresh data.");
            }
        };
    }
}
