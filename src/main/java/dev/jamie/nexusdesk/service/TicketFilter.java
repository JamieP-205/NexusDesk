package dev.jamie.nexusdesk.service;

import java.time.LocalDate;

/** The search and filter choices on the ticket list. Empty strings and nulls mean "any". */
public record TicketFilter(String q, String status, String priority, String category,
                           Long creator, Long assignee, LocalDate from, LocalDate to) {
    public static TicketFilter none() { return new TicketFilter("", "", "", "", null, null, null, null); }

    public boolean isEmpty() {
        return blank(q) && blank(status) && blank(priority) && blank(category)
                && creator == null && assignee == null && from == null && to == null;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
