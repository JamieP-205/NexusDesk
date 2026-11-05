package dev.jamie.nexusdesk;

import dev.jamie.nexusdesk.config.DatabaseMode;
import dev.jamie.nexusdesk.config.LoginThrottle;
import dev.jamie.nexusdesk.config.PasswordHashing;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import static org.assertj.core.api.Assertions.*;

/** Plain unit tests: no Spring context, so each one controls its own database or clock. */
class MigrationAndModeTest {
    static JdbcDataSource database(String name) {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        return ds;
    }

    @Test @SuppressWarnings("deprecation")
    void migrationTagsOldHashesWithoutChangingThem() {
        var ds = database("migrate-old-hashes");
        Flyway.configure().dataSource(ds).target("2").load().migrate();
        var db = new JdbcTemplate(ds);
        // A database exactly as version 0.1.0 left it.
        String old = new Pbkdf2PasswordEncoder("", 16, 600_000, 256).encode("Old-password-456");
        db.update("INSERT INTO users(name,email,role,password_hash) VALUES ('Old','old@example.test','EMPLOYEE',?)", old);
        Flyway.configure().dataSource(ds).load().migrate();
        String stored = db.queryForObject("SELECT password_hash FROM users", String.class);
        assertThat(stored).isEqualTo("{pbkdf2-sha1}" + old);
        var encoder = PasswordHashing.encoder();
        assertThat(encoder.matches("Old-password-456", stored)).isTrue();
        assertThat(encoder.matches("wrong", stored)).isFalse();
        assertThat(encoder.upgradeEncoding(stored)).isTrue();
        String fresh = encoder.encode("Old-password-456");
        assertThat(fresh).startsWith("{pbkdf2-sha256}");
        assertThat(encoder.upgradeEncoding(fresh)).isFalse();
        // Running the migrations again changes nothing.
        Flyway.configure().dataSource(ds).load().migrate();
        assertThat(db.queryForObject("SELECT password_hash FROM users", String.class)).isEqualTo(stored);
    }

    @Test void theNewHashReallyIsSha256NotTheOldDefault() {
        // Compared against both a plain SHA-256 encoder and the exact 0.1.0 configuration.
        var sha256 = new Pbkdf2PasswordEncoder("", 16, 600_000, Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        @SuppressWarnings("deprecation") var sha1 = new Pbkdf2PasswordEncoder("", 16, 600_000, 256);
        String hash = PasswordHashing.encoder().encode("Some-password-1").substring("{pbkdf2-sha256}".length());
        assertThat(sha256.matches("Some-password-1", hash)).isTrue();
        assertThat(sha1.matches("Some-password-1", hash)).isFalse();
    }

    @Test void recognisesDemoDatabasesOldAndNew() {
        var ds = database("mode-check");
        Flyway.configure().dataSource(ds).load().migrate();
        var db = new JdbcTemplate(ds);
        var mode = new DatabaseMode(db);
        assertThat(mode.hasNoUsers()).isTrue();
        assertThat(mode.isDemo()).isFalse();
        db.update("INSERT INTO users(name,email,role,password_hash) VALUES ('T','tech@example.test','TECHNICIAN','x'),('E','employee@example.test','EMPLOYEE','x')");
        assertThat(mode.isDemo()).isFalse();
        db.update("INSERT INTO assets(type,manufacturer,model,serial_number,purchase_date) VALUES ('Laptop','L','E14','DEMO-1001',CURRENT_DATE)");
        assertThat(mode.looksLikeUnmarkedDemo()).isTrue();
        assertThat(mode.isMarkedDemo()).isFalse();
        mode.markDemo();
        mode.markDemo();
        assertThat(mode.isMarkedDemo()).isTrue();
    }

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-01T09:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test void throttleWindowSlidesAndSuccessClearsTheAccount() {
        var clock = new MutableClock();
        var throttle = new LoginThrottle(clock, 3, 5, Duration.ofMinutes(15));
        for (int i = 0; i < 3; i++) throttle.failed("A@Example.test ", "10.0.0.1");
        assertThat(throttle.retryAfter("a@example.test", "10.0.0.1")).isEqualTo(Duration.ofMinutes(15));
        assertThat(throttle.retryAfter("a@example.test", "10.0.0.2")).isZero();
        clock.now = clock.now.plus(Duration.ofMinutes(10));
        assertThat(throttle.retryAfter("a@example.test", "10.0.0.1")).isEqualTo(Duration.ofMinutes(5));
        clock.now = clock.now.plus(Duration.ofMinutes(5));
        assertThat(throttle.retryAfter("a@example.test", "10.0.0.1")).isZero();
        throttle.failed("b@example.test", "10.0.0.3");
        throttle.failed("b@example.test", "10.0.0.3");
        throttle.succeeded("b@example.test", "10.0.0.3");
        throttle.failed("b@example.test", "10.0.0.3");
        assertThat(throttle.retryAfter("b@example.test", "10.0.0.3")).isZero();
        // The per-address limit still counts every failure.
        throttle.failed("c@example.test", "10.0.0.3");
        throttle.failed("d@example.test", "10.0.0.3");
        assertThat(throttle.retryAfter("e@example.test", "10.0.0.3")).isPositive();
    }

    @Test void guessesInFlightCountTowardsTheLimit() {
        var throttle = new LoginThrottle(new MutableClock(), 3, 20, Duration.ofMinutes(15));
        var wait = new Duration[1];
        // Three simultaneous guesses are allowed to start; a fourth isn't, even before any has failed.
        var first = throttle.tryStart("a@example.test", "10.0.0.9", wait);
        var second = throttle.tryStart("a@example.test", "10.0.0.9", wait);
        var third = throttle.tryStart("a@example.test", "10.0.0.9", wait);
        assertThat(first).isNotNull(); assertThat(second).isNotNull(); assertThat(third).isNotNull();
        assertThat(throttle.tryStart("a@example.test", "10.0.0.9", wait)).isNull();
        first.failed(); second.failed(); third.failed();
        third.failed(); // finishing twice is ignored
        assertThat(throttle.tryStart("a@example.test", "10.0.0.9", wait)).isNull();
        assertThat(wait[0]).isEqualTo(Duration.ofMinutes(15));
        // A success releases its slot and clears the account count.
        var other = throttle.tryStart("b@example.test", "10.0.0.9", wait);
        other.succeeded();
        assertThat(throttle.retryAfter("b@example.test", "10.0.0.9")).isZero();
    }
}
