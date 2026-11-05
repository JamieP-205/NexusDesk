package dev.jamie.nexusdesk.model;

import java.time.LocalDateTime;

/** One row of an asset's ownership history. returnedAt is null for the current owner. */
public record Assignment(String name, LocalDateTime assignedAt, LocalDateTime returnedAt) {}
