package dev.jamie.nexusdesk.config;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/**
 * Wraps the normal password check with {@link LoginThrottle}. Because the throttle sits inside the
 * authentication step, every route that leads to a password check is covered, whatever the URL looks like.
 */
final class ThrottledAuthenticationProvider implements AuthenticationProvider {
    private static final Logger LOG = LoggerFactory.getLogger(ThrottledAuthenticationProvider.class);
    private final AuthenticationProvider delegate;
    private final LoginThrottle throttle;

    /** Thrown instead of checking the password while an account or address is throttled. */
    static final class Throttled extends AuthenticationException {
        private final Duration wait;
        Throttled(Duration wait) { super("Too many failed sign-in attempts"); this.wait = wait; }
        long minutes() { return Math.max(1, (wait.toSeconds() + 59) / 60); }
    }

    ThrottledAuthenticationProvider(AuthenticationProvider delegate, LoginThrottle throttle) {
        this.delegate = delegate; this.throttle = throttle;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String address = authentication.getDetails() instanceof WebAuthenticationDetails details
                ? details.getRemoteAddress() : "unknown";
        Duration[] wait = new Duration[1];
        var attempt = throttle.tryStart(authentication.getName(), address, wait);
        if (attempt == null) {
            LOG.warn("Refused a sign-in attempt from {} after repeated failures", address);
            throw new Throttled(wait[0]);
        }
        try {
            Authentication result = delegate.authenticate(authentication);
            if (result == null) { attempt.failed(); return null; }
            attempt.succeeded();
            return result;
        } catch (RuntimeException e) {
            attempt.failed();
            throw e;
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
