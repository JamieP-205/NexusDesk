package dev.jamie.nexusdesk.hosted;

import dev.jamie.nexusdesk.config.DemoSeeder;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Creates, finds and removes visitor sandboxes.
 *
 * <p>The fictional data is seeded once into a template database and exported as SQL. Each new
 * sandbox is a fresh in-memory H2 database that replays that SQL, which takes milliseconds and
 * doesn't repeat the slow password hashing. Sandboxes expire after a period without requests, there's
 * a hard cap on how many exist at once, and new ones are rate limited, so memory use stays bounded.</p>
 */
@Component
@Profile("hosted")
public class SandboxRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(SandboxRegistry.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<String, Sandbox> sandboxes = new ConcurrentHashMap<>();
    private final Deque<Map.Entry<Instant, String>> recentCreations = new ArrayDeque<>();
    private final Map<String, String> owners = new ConcurrentHashMap<>();
    /** Per visitor address, as reported by the hosting proxy. */
    private static final int CLIENT_STARTS_PER_MINUTE = 6, CLIENT_LIVE = 10;
    /** When full, only a sandbox unused for this long can make way for a newcomer. */
    private static final Duration EVICT_AFTER_IDLE = Duration.ofMinutes(5);
    private final DemoSeeder seeder;
    private final Clock clock;
    private final int maxSandboxes;
    private final Duration idle;
    private final Duration maxAge;
    private final int creationsPerMinute;
    private volatile List<String> template = List.of();
    private volatile Map<String, Long> roleUsers = Map.of();

    public SandboxRegistry(DemoSeeder seeder, Clock clock,
                           @Value("${nexusdesk.hosted.max-sandboxes:100}") int maxSandboxes,
                           @Value("${nexusdesk.hosted.idle:60m}") Duration idle,
                           @Value("${nexusdesk.hosted.max-age:4h}") Duration maxAge,
                           @Value("${nexusdesk.hosted.creations-per-minute:30}") int creationsPerMinute) {
        this.seeder = seeder; this.clock = clock; this.maxSandboxes = maxSandboxes;
        this.idle = idle; this.maxAge = maxAge; this.creationsPerMinute = creationsPerMinute;
    }

    /** Thrown when the demo is at capacity or new sandboxes are arriving too quickly. */
    public static final class Busy extends RuntimeException {
        Busy(String message) { super(message); }
    }

    public Duration idleTimeout() { return idle; }
    public int size() { return sandboxes.size(); }

    /** The seeded account for "employee", "technician" or "admin". */
    public Long userFor(String role) { return roleUsers.get(role); }

    /** Seeds a fresh template. Runs at startup and hourly, so the sample dates stay recent. */
    @Scheduled(initialDelayString = "${nexusdesk.hosted.template-refresh:60m}", fixedDelayString = "${nexusdesk.hosted.template-refresh:60m}")
    public synchronized void refreshTemplate() {
        JdbcDataSource db = memory("tpl_" + token());
        try {
            Flyway.configure().dataSource(db).load().migrate();
            SandboxContext.bindDirect(db);
            seeder.seed();
            var roles = new HashMap<String, Long>();
            try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
                try (ResultSet rs = s.executeQuery("SELECT id, email FROM users")) {
                    while (rs.next()) {
                        switch (rs.getString(2)) {
                            case "employee@example.test" -> roles.put("employee", rs.getLong(1));
                            case "tech@example.test" -> roles.put("technician", rs.getLong(1));
                            case "admin@example.test" -> roles.put("admin", rs.getLong(1));
                            default -> { }
                        }
                    }
                }
                var statements = new ArrayList<String>();
                try (ResultSet rs = s.executeQuery("SCRIPT NOPASSWORDS NOSETTINGS")) {
                    while (rs.next()) statements.add(rs.getString(1));
                }
                template = List.copyOf(statements);
                roleUsers = Map.copyOf(roles);
            }
            LOG.info("Demo template ready: {} statements", template.size());
        } catch (SQLException e) {
            throw new IllegalStateException("Couldn't build the demo template", e);
        } finally {
            SandboxContext.clear();
            shutdown(db);
        }
    }

    public Sandbox create() { return create(null); }

    /**
     * Creates a sandbox. Checks and creation happen under one lock, so simultaneous starts can't exceed
     * the caps. {@code client} is the visitor's proxy-reported address, or null when it isn't known, in
     * which case only the overall limits apply.
     */
    public synchronized Sandbox create(String client) {
        Instant now = clock.instant();
        while (!recentCreations.isEmpty() && recentCreations.peekFirst().getKey().isBefore(now.minusSeconds(60))) recentCreations.removeFirst();
        if (recentCreations.size() >= creationsPerMinute) throw new Busy("Lots of people are starting demos right now. Please try again in a minute.");
        if (client != null && (recentCreations.stream().filter(e -> e.getValue().equals(client)).count() >= CLIENT_STARTS_PER_MINUTE
                || owners.values().stream().filter(client::equals).count() >= CLIENT_LIVE)) {
            throw new Busy("You've started several demos in a short time. Please use the one you have, or try again in a minute.");
        }
        if (sandboxes.size() >= maxSandboxes) evictIdle(now);
        if (sandboxes.size() >= maxSandboxes) throw new Busy("The demo is full right now. Please try again in a few minutes.");
        recentCreations.addLast(Map.entry(now, client == null ? "" : client));
        String id = token();
        JdbcDataSource db = memory("sbx_" + id);
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            for (String sql : template) s.execute(sql);
        } catch (SQLException e) {
            shutdown(db);
            throw new IllegalStateException("Couldn't create a demo sandbox", e);
        }
        Sandbox sandbox = new Sandbox(id, db, now);
        sandboxes.put(id, sandbox);
        if (client != null) owners.put(id, client);
        return sandbox;
    }

    /** Returns the live sandbox and records the visit, or null if it has expired or been removed. */
    public Sandbox find(String id) {
        if (id == null) return null;
        Sandbox sandbox = sandboxes.get(id);
        if (sandbox == null) return null;
        Instant now = clock.instant();
        if (expired(sandbox, now)) { remove(id); return null; }
        sandbox.touch(now);
        return sandbox;
    }

    public void remove(String id) {
        Sandbox sandbox = id == null ? null : sandboxes.remove(id);
        if (id != null) owners.remove(id);
        if (sandbox != null) shutdown(sandbox.dataSource());
    }

    /** Deletes every sandbox (used by tests). */
    public void removeAll() { List.copyOf(sandboxes.keySet()).forEach(this::remove); }

    @Scheduled(fixedDelayString = "${nexusdesk.hosted.cleanup:60s}")
    public void removeExpired() {
        Instant now = clock.instant();
        sandboxes.values().stream().filter(s -> expired(s, now)).map(Sandbox::id).toList().forEach(this::remove);
    }

    private boolean expired(Sandbox s, Instant now) {
        return s.lastSeen().plus(idle).isBefore(now) || s.created().plus(maxAge).isBefore(now);
    }

    /**
     * At capacity, the sandbox unused for longest gives way, but only if nobody has used it for a few
     * minutes, so newcomers can't push out someone who is using the demo.
     */
    private void evictIdle(Instant now) {
        sandboxes.values().stream().filter(s -> s.lastSeen().plus(EVICT_AFTER_IDLE).isBefore(now))
                .min(Comparator.comparing(Sandbox::lastSeen)).ifPresent(s -> remove(s.id()));
    }

    private static JdbcDataSource memory(String name) {
        var db = new JdbcDataSource();
        db.setURL("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1");
        db.setUser("sa");
        return db;
    }

    private static void shutdown(JdbcDataSource db) {
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("SHUTDOWN");
        } catch (SQLException ignored) {
            // Already closed.
        }
    }

    private static String token() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
