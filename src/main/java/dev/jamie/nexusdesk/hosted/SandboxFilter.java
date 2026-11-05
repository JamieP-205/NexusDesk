package dev.jamie.nexusdesk.hosted;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs before Spring Security, so sign-in checks, permissions and every query use the visitor's own
 * sandbox. If the sandbox has expired, the session is ended and the visitor is sent to start again.
 */
final class SandboxFilter extends OncePerRequestFilter {
    static final String SESSION_KEY = "nexusdesk.sandbox";
    private final SandboxRegistry registry;

    SandboxFilter(SandboxRegistry registry) { this.registry = registry; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var session = request.getSession(false);
        String id = session == null ? null : (String) session.getAttribute(SESSION_KEY);
        Sandbox sandbox = registry.find(id);
        if (id != null && sandbox == null) {
            session.invalidate();
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (!path.equals("/login") && !path.equals("/demo/start") && !path.equals("/style.css") && !path.equals("/app.js")) {
                response.sendRedirect(request.getContextPath() + "/login?ended");
                return;
            }
        }
        try {
            if (sandbox != null) SandboxContext.bind(sandbox);
            chain.doFilter(request, response);
        } finally {
            SandboxContext.clear();
        }
    }
}
