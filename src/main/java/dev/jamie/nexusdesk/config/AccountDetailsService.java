package dev.jamie.nexusdesk.config;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Loads accounts for sign-in. Because it is also a {@link UserDetailsPasswordService}, Spring Security
 * calls {@link #updatePassword} after a successful sign-in whenever the stored hash uses an older
 * algorithm. That is how version 0.1.0's SHA-1 hashes become SHA-256 without anyone resetting a password.
 */
@Component
public class AccountDetailsService implements UserDetailsService, UserDetailsPasswordService {
    private static final Logger LOG = LoggerFactory.getLogger(AccountDetailsService.class);
    private final JdbcTemplate db;

    public AccountDetailsService(JdbcTemplate db) { this.db = db; }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return db.query("SELECT email, password_hash, role, enabled FROM users WHERE email=?", (rs, row) ->
                        User.withUsername(rs.getString("email")).password(rs.getString("password_hash"))
                                .roles(rs.getString("role")).disabled(!rs.getBoolean("enabled")).build(),
                        normalise(email)).stream().findFirst()
                .orElseThrow(() -> new UsernameNotFoundException("Invalid login"));
    }

    @Override
    public UserDetails updatePassword(UserDetails user, String newEncodedPassword) {
        db.update("UPDATE users SET password_hash=? WHERE email=?", newEncodedPassword, user.getUsername());
        LOG.info("Upgraded a stored password hash to {}", PasswordHashing.CURRENT);
        return User.withUserDetails(user).password(newEncodedPassword).build();
    }

    static String normalise(String email) { return email == null ? "" : email.strip().toLowerCase(Locale.ROOT); }
}
