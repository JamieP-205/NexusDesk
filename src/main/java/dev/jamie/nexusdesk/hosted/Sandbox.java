package dev.jamie.nexusdesk.hosted;

import java.time.Instant;
import org.h2.jdbcx.JdbcDataSource;

/** One visitor's private, in-memory copy of the demo database. */
public final class Sandbox {
    private final String id;
    private final JdbcDataSource dataSource;
    private final Instant created;
    private volatile Instant lastSeen;

    Sandbox(String id, JdbcDataSource dataSource, Instant created) {
        this.id = id; this.dataSource = dataSource; this.created = created; this.lastSeen = created;
    }

    public String id() { return id; }
    JdbcDataSource dataSource() { return dataSource; }
    public Instant created() { return created; }
    public Instant lastSeen() { return lastSeen; }
    void touch(Instant now) { lastSeen = now; }
}
