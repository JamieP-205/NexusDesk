package dev.jamie.nexusdesk.service;

import dev.jamie.nexusdesk.model.Account;
import java.util.List;

public final class Rules {
    public static final List<String> CATEGORIES = List.of("Hardware", "Software", "Account", "Network", "Printer", "Other");
    public static final List<String> PRIORITIES = List.of("Low", "Medium", "High", "Critical");
    public static final List<String> STATUSES = List.of("Open", "Assigned", "In Progress", "Waiting", "Resolved", "Closed");
    public static final List<String> ASSET_TYPES = List.of("Laptop", "Desktop", "Monitor", "Phone", "Tablet", "Keyboard", "Mouse", "Other");
    private Rules() {}

    public static void staff(Account actor) {
        if (!actor.enabled() || !actor.isStaff()) throw new Problem(403, "This action needs IT staff access.");
    }
    public static void admin(Account actor) {
        if (!actor.enabled() || !actor.isAdmin()) throw new Problem(403, "This action needs administrator access.");
    }
}
