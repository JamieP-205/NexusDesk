package dev.jamie.nexusdesk.config;

import java.util.Map;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm;

/**
 * Password hashes are stored as "{id}hash" so the algorithm travels with each hash.
 *
 * <ul>
 *   <li>{@code {pbkdf2-sha256}} is used for every new or upgraded password:
 *       PBKDF2-HMAC-SHA256, 600,000 iterations, 16-byte salt, 256-bit output (OWASP's current guidance
 *       for PBKDF2-HMAC-SHA256).</li>
 *   <li>{@code {pbkdf2-sha1}} only verifies hashes made by version 0.1.0 (see {@link LegacySha1PasswordVerifier}).
 *       Migration V3 tags those hashes, and a successful sign-in replaces them with SHA-256.</li>
 * </ul>
 */
public final class PasswordHashing {
    public static final String CURRENT = "pbkdf2-sha256";
    public static final String LEGACY = "pbkdf2-sha1";

    private PasswordHashing() {}

    public static PasswordEncoder encoder() {
        // The non-deprecated constructor, with the algorithm stated rather than defaulted.
        var current = new Pbkdf2PasswordEncoder("", 16, 600_000, SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        // No default for untagged hashes: after migration V3 every stored hash has an id, and an
        // unknown format should fail closed rather than be guessed.
        var delegating = new DelegatingPasswordEncoder(CURRENT, Map.of(CURRENT, current, LEGACY, new LegacySha1PasswordVerifier()));
        delegating.setDefaultPasswordEncoderForMatches(new PasswordEncoder() {
            @Override public String encode(CharSequence raw) { throw new UnsupportedOperationException(); }
            @Override public boolean matches(CharSequence raw, String encoded) { return false; }
        });
        return delegating;
    }
}
