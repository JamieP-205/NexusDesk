package dev.jamie.nexusdesk.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs before Spring Security checks the password. A blocked attempt is turned away without the
 * password being tested at all, so guessing gains nothing even if the guess would have been right.
 */
final class LoginThrottleFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(LoginThrottleFilter.class);
    private final LoginThrottle throttle;

    LoginThrottleFilter(LoginThrottle throttle) { this.throttle = throttle; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && (request.getContextPath() + "/login").equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var wait = throttle.retryAfter(request.getParameter("username"), request.getRemoteAddr());
        if (!wait.isZero()) {
            LOG.warn("Blocked a sign-in attempt after repeated failures from {}", request.getRemoteAddr());
            long minutes = Math.max(1, (wait.toSeconds() + 59) / 60);
            response.sendRedirect(request.getContextPath() + "/login?locked=" + minutes);
            return;
        }
        chain.doFilter(request, response);
    }
}
