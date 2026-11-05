package dev.jamie.nexusdesk.web;

import dev.jamie.nexusdesk.model.Account;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * The "Things to try" checklist shown in the demos, so visitors find what each role can do.
 * Only the demo and hosted profiles use it; the normal app never shows it or records anything.
 *
 * <p>In the browser demo, progress lives in the visitor's session, which survives switching roles
 * and ends with their sandbox. In the local demo, people switch roles by signing out and in again,
 * so progress is kept per account in memory instead, until the app stops.
 */
@Component
public class Guide {
    public static final String SESSION_KEY = "nexusdesk.guide";

    public record Step(String key, String label, String detail, String href, boolean done) {}
    public record View(String heading, String intro, List<Step> steps, long done, String journey) {}

    private final boolean enabled;
    private final boolean hosted;
    private final Map<Long, Set<String>> localProgress = new ConcurrentHashMap<>();

    public Guide(Environment env) {
        this.enabled = env.matchesProfiles("demo | hosted");
        this.hosted = env.matchesProfiles("hosted");
    }

    /** Records that this person finished a step, e.g. "raise" or "take". Does nothing outside the demos. */
    public void done(HttpServletRequest request, Account who, String step) {
        if (enabled) progress(request, who, true).add(who.role() + ":" + step);
    }

    /** Clears the browser demo's checklist, for "Reset demo". */
    public void clear(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) session.removeAttribute(SESSION_KEY);
    }

    /** The checklist for this person's role, or null outside the demos. */
    public View view(HttpServletRequest request, Account who) {
        if (!enabled || who == null) return null;
        Set<String> progress = progress(request, who, false);
        List<Step> steps = switch (who.role()) {
            case "ADMIN" -> List.of(
                    step(progress, who, "account", "Create an account",
                            "Add a new starter on the People page and choose their role.", "/users"),
                    step(progress, who, "access", "Change someone's access",
                            "Open an account to change its role or disable it. A disabled person is signed out straight away.", "/users"),
                    step(progress, who, "equipment", "Assign equipment to someone",
                            "Open an item that's available and choose who it's for. Returning it keeps the history.", "/assets"));
            case "TECHNICIAN" -> List.of(
                    step(progress, who, "take", "Pick up a ticket from the queue",
                            "Open an unassigned ticket and press Assign to me.", "/tickets?status=Open"),
                    step(progress, who, "resolve", "Resolve a ticket",
                            "On a ticket you're working on, say what fixed it. The person who raised it can then close it or reopen it.",
                            "/tickets?assignee=" + who.id()),
                    step(progress, who, "comment", "Reply to someone",
                            "Add a comment to a ticket. The person who raised it sees it in the conversation.", "/tickets"));
            default -> List.of(
                    step(progress, who, "raise", "Raise a ticket",
                            "Describe a problem. It goes straight into IT's queue.", "/tickets/new"),
                    step(progress, who, "comment", "Add a comment to one of your tickets",
                            "Ask a question or add detail. Only you and IT can see it.", "/tickets"),
                    step(progress, who, "confirm", "Close a resolved ticket, or reopen it",
                            "Open a Resolved ticket and confirm it's fixed, or reopen it if the problem is still there.",
                            "/tickets?status=Resolved"));
        };
        String heading = switch (who.role()) {
            case "ADMIN" -> "Things to try as an administrator";
            case "TECHNICIAN" -> "Things to try as an IT technician";
            default -> "Things to try as an employee";
        };
        String intro = switch (who.role()) {
            case "ADMIN" -> "Administrators manage accounts and equipment, and can also do everything a technician can.";
            case "TECHNICIAN" -> "Technicians see every ticket, pick them up and resolve them. They can't manage accounts or equipment.";
            default -> "Employees see only their own tickets and equipment. Here's what you can do:";
        };
        String journey = hosted
                ? "Try the whole journey: raise a ticket as the employee, use View as → Technician to pick it up and resolve it, then switch back to Employee to close it."
                : "Try the whole journey: raise a ticket as employee@example.test, sign out and sign in as tech@example.test to pick it up and resolve it, then sign back in as the employee to close it.";
        return new View(heading, intro, steps, steps.stream().filter(Step::done).count(), journey);
    }

    private static Step step(Set<String> progress, Account who, String key, String label, String detail, String href) {
        return new Step(key, label, detail, href, progress.contains(who.role() + ":" + key));
    }

    @SuppressWarnings("unchecked")
    private Set<String> progress(HttpServletRequest request, Account who, boolean create) {
        if (!hosted) return localProgress.computeIfAbsent(who.id(), id -> ConcurrentHashMap.newKeySet());
        var session = request.getSession(create);
        if (session == null) return Set.of();
        var set = (Set<String>) session.getAttribute(SESSION_KEY);
        if (set == null) {
            set = ConcurrentHashMap.newKeySet();
            if (create) session.setAttribute(SESSION_KEY, set);
        }
        return set;
    }
}
