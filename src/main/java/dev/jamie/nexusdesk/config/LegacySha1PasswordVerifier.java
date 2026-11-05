package dev.jamie.nexusdesk.config;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Checks passwords against hashes written by version 0.1.0, and never writes new ones.
 *
 * <p>0.1.0 called {@code new Pbkdf2PasswordEncoder("", 16, 600_000, 256)}. That deprecated constructor
 * quietly picked PBKDF2-HMAC-SHA1 and kept the 256-bit output. Spring's replacement constructor can't
 * reproduce that pairing (choosing SHA-1 there also shrinks the output to 160 bits), so this class
 * repeats the old calculation with the JDK directly. The stored format is hex(salt[16] + hash[32]).</p>
 */
final class LegacySha1PasswordVerifier implements PasswordEncoder {
    private static final int SALT_BYTES = 16;
    private static final int ITERATIONS = 600_000;
    private static final int HASH_BITS = 256;

    @Override
    public String encode(CharSequence rawPassword) {
        throw new UnsupportedOperationException("New passwords are always hashed with " + PasswordHashing.CURRENT);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) return false;
        byte[] stored;
        try { stored = HexFormat.of().parseHex(encodedPassword); } catch (IllegalArgumentException e) { return false; }
        if (stored.length != SALT_BYTES + HASH_BITS / 8) return false;
        byte[] salt = java.util.Arrays.copyOfRange(stored, 0, SALT_BYTES);
        byte[] expected = java.util.Arrays.copyOfRange(stored, SALT_BYTES, stored.length);
        try {
            // The old encoder's secret was "", so the salt alone is used.
            var spec = new PBEKeySpec(rawPassword.toString().toCharArray(), salt, ITERATIONS, HASH_BITS);
            byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
            spec.clearPassword();
            return MessageDigest.isEqual(expected, actual);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA1 is unavailable", e);
        }
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) { return true; }
}
