package dev.jamie.nexusdesk.model;

import java.time.LocalDateTime;

public record Ticket(long id, String title, String description, String category, String priority,
                     String status, long creatorId, String creatorName, Long assigneeId, String assigneeName,
                     String resolution, LocalDateTime createdAt, LocalDateTime updatedAt,
                     LocalDateTime resolvedAt, int version) {
    public boolean isFinished() { return status.equals("Resolved") || status.equals("Closed"); }
}
