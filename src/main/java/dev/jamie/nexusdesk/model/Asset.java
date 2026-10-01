package dev.jamie.nexusdesk.model;

import java.time.LocalDate;

public record Asset(long id, String type, String manufacturer, String model, String serialNumber,
                    LocalDate purchaseDate, String status, int version, Long ownerId, String ownerName) {}
