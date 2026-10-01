package dev.jamie.nexusdesk.service;

import java.util.List;
import java.util.Map;

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
    public static void check(String from, String to) {
        if (!from.equals(to) && !next(from).contains(to)) {
            throw Problem.invalid("A ticket can't move from " + from + " to " + to + ".");
        }
    }
}
