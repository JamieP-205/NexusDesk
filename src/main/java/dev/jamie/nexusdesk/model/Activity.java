package dev.jamie.nexusdesk.model;

import java.time.LocalDateTime;

public record Activity(long id, String name, String body, LocalDateTime createdAt) {}
