package dev.jamie.nexusdesk.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Slows down password guessing by counting failed sign-ins in a sliding window.
 *
 * <p>Two limits apply: failures for one email from one network address (stops guessing a single
 * account), and all failures from one address (stops trying many accounts). Keying the account limit
 * by address as well means someone elsewhere can't lock a colleague out just by typing their email.</p>
 *
 * <p>Checking and reserving happen in one synchronized step, and attempts still being checked count
 * towards the limit, so firing many guesses at the same moment can't slip past it.
 * Counts are kept in memory: they reset on restart and aren't shared between app instances.</p>
 */
@Component
public class LoginThrottle {
    private static final int MAX_TRACKED_KEYS = 10_000;
    private final Clock clock;
    private final int perAccount;
    private final int perAddress;
    private final Duration window;
    private final Map<String, Deque<Instant>> failures = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Deque<Instant>> eldest) { return size() > MAX_TRACKED_KEYS; }
    };
    private final Map<String, Integer> inFlight = new HashMap<>();

    public LoginThrottle(Clock clock,
                         @Value("${nexusdesk.login.max-failures-per-account:5}") int perAccount,
                         @Value("${nexusdesk.login.max-failures-per-address:20}") int perAddress,
                         @Value("${nexusdesk.login.window:15m}") Duration window) {
        this.clock = clock; this.perAccount = perAccount; this.perAddress = perAddress; this.window = window;
    }

    /** A reserved sign-in attempt. Exactly one of {@link #failed()} or {@link #succeeded()} must follow. */
    public final class Attempt {
        private final String account, address;
        private boolean done;
        private Attempt(String account, String address) { this.account = account; this.address = address; }
        public void failed() { finish(this, true); }
        public void succeeded() { finish(this, false); }
    }

    /**
     * Reserves an attempt, or returns null with {@code wait[0]} set to how long the caller must wait.
     * A null return means the password must not be checked at all.
     */
    public synchronized Attempt tryStart(String email, String address, Duration[] wait) {
        Instant now = clock.instant();
        String account = accountKey(email, address), overall = addressKey(address);
        Duration a = wait(account, perAccount, now), b = wait(overall, perAddress, now);
        Duration longest = a.compareTo(b) >= 0 ? a : b;
        if (!longest.isZero()) { wait[0] = longest; return null; }
        inFlight.merge(account, 1, Integer::sum);
        inFlight.merge(overall, 1, Integer::sum);
        return new Attempt(account, overall);
    }

    /** Test and diagnostics helper: how long this email+address must wait now. */
    public synchronized Duration retryAfter(String email, String address) {
        Instant now = clock.instant();
        Duration a = wait(accountKey(email, address), perAccount, now), b = wait(addressKey(address), perAddress, now);
        return a.compareTo(b) >= 0 ? a : b;
    }

    /** Records a failure directly (used by tests to set up a state). */
    public synchronized void failed(String email, String address) {
        Instant now = clock.instant();
        record(accountKey(email, address), now);
        record(addressKey(address), now);
    }

    /** A correct password clears that account's count. The address count still expires on its own. */
    public synchronized void succeeded(String email, String address) { failures.remove(accountKey(email, address)); }

    private synchronized void finish(Attempt attempt, boolean failed) {
        if (attempt.done) return;
        attempt.done = true;
        release(attempt.account);
        release(attempt.address);
        if (failed) {
            Instant now = clock.instant();
            record(attempt.account, now);
            record(attempt.address, now);
        } else {
            failures.remove(attempt.account);
        }
    }

    private void release(String key) { inFlight.computeIfPresent(key, (k, n) -> n > 1 ? n - 1 : null); }

    private Duration wait(String key, int limit, Instant now) {
        Deque<Instant> times = prune(key, now);
        int failed = times == null ? 0 : times.size();
        int pending = inFlight.getOrDefault(key, 0);
        if (failed + pending < limit) return Duration.ZERO;
        // Blocked by attempts still in progress: ask them to retry shortly rather than wait a full window.
        if (failed < limit) return Duration.ofSeconds(1);
        return Duration.between(now, times.peekFirst().plus(window));
    }
    private void record(String key, Instant now) {
        Deque<Instant> times = prune(key, now);
        if (times == null) { times = new ArrayDeque<>(); failures.put(key, times); }
        times.addLast(now);
    }
    private Deque<Instant> prune(String key, Instant now) {
        Deque<Instant> times = failures.get(key);
        if (times == null) return null;
        while (!times.isEmpty() && !times.peekFirst().plus(window).isAfter(now)) times.removeFirst();
        if (times.isEmpty()) { failures.remove(key); return null; }
        return times;
    }
    private static String accountKey(String email, String address) { return "account:" + AccountDetailsService.normalise(email) + "|" + address; }
    private static String addressKey(String address) { return "address:" + address; }
}
