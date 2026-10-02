package dev.jamie.nexusdesk.service;

import dev.jamie.nexusdesk.model.Account;
import java.util.List;

public final class Rules {
    public static final List<String> CATEGORIES = List.of("Hardware", "Software", "Account", "Network", "Printer", "Other");
    public static final List<String> PRIORITIES = List.of("Low", "Medium", "High", "Critical");
    public static final List<String> STATUSES = List.of("Open", "Assigned", "In Progress", "Waiting", "Resolved", "Closed");
    public static final List<String> ASSET_TYPES = List.of("Laptop", "Desktop", "Monitor", "Phone", "Tablet", "Keyboard", "Mouse", "Other");
    private Rules() {}

    public static String text(String value, String field, int max) {
        if (value == null || value.isBlank() || value.strip().length() > max) {
            throw Problem.invalid(field + " is required and must be no more than " + max + " characters.");
        }
        return value.strip();
    }
    public static String choice(String value, List<String> choices, String field) {
        if (!choices.contains(value)) throw Problem.invalid("Choose a valid " + field + ".");
        return value;
    }
    public static void staff(Account actor) {
        if (!actor.enabled() || !actor.isStaff()) throw new Problem(403, "This action needs IT staff access.");
    }
    public static void admin(Account actor) {
        if (!actor.enabled() || !actor.isAdmin()) throw new Problem(403, "This action needs administrator access.");
    }
}
