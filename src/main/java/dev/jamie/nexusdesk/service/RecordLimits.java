package dev.jamie.nexusdesk.service;

/**
 * Optional caps on how many rows a database may hold. The normal app has none; the hosted demo
 * supplies limits so one visitor can't fill the server's memory.
 */
public interface RecordLimits {
    /** Throws a {@link ValidationProblem} if another row can't be added to this table. */
    void check(String table);

    RecordLimits NONE = table -> { };
}
