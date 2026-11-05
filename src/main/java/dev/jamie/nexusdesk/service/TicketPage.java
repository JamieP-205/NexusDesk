package dev.jamie.nexusdesk.service;

import dev.jamie.nexusdesk.model.Ticket;
import java.util.List;

/** One page of search results plus enough numbers to draw the page links. */
public record TicketPage(List<Ticket> items, int page, int size, long total) {
    public int totalPages() { return (int) Math.max(1, (total + size - 1) / size); }
    public boolean hasPrevious() { return page > 1; }
    public boolean hasNext() { return page < totalPages(); }
    public long firstItem() { return total == 0 ? 0 : (long) (page - 1) * size + 1; }
    public long lastItem() { return (long) (page - 1) * size + items.size(); }
}
