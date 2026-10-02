package dev.jamie.nexusdesk.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

final class AccountStatusFilter extends OncePerRequestFilter {
    private final JdbcTemplate db;
    AccountStatusFilter(JdbcTemplate db) { this.db = db; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            var roles = db.queryForList("SELECT role FROM users WHERE email=? AND enabled=TRUE", String.class, auth.getName());
            if (roles.isEmpty() || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_" + roles.get(0)))) {
                new SecurityContextLogoutHandler().logout(request, response, auth);
                response.sendRedirect("/login?expired");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
