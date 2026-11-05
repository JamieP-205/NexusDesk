package dev.jamie.nexusdesk.service;

import java.util.List;
import java.util.Map;

/**
 * The ticket lifecycle. The assignment rule lives alongside it:
 * an Open ticket never has a technician, and every other status must have one.
 */
public final class TicketFlow {
    private static final Map<String, List<String>> NEXT = Map.of(
            "Open", List.of("Assigned"),
            "Assigned", List.of("In Progress", "Waiting", "Resolved"),
            "In Progress", List.of("Waiting", "Resolved"),
            "Waiting", List.of("In Progress", "Resolved"),
            "Resolved", List.of("Closed", "Open"),
            "Closed", List.of("Open"));
    private TicketFlow() {}
    public static List<String> next(String status) { return NEXT.getOrDefault(status, List.of()); }
    public static boolean allowed(String from, String to) { return from.equals(to) || next(from).contains(to); }
    public static boolean needsTechnician(String status) { return !status.equals("Open"); }
    public static boolean isFinished(String status) { return status.equals("Resolved") || status.equals("Closed"); }
}
