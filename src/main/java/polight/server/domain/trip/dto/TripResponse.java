package polight.server.domain.trip.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import polight.server.domain.concern.entity.Concern;
import polight.server.domain.trip.entity.TripStatus;

public record TripResponse(
    UUID id,
    String name,
    LocalDate startDate,
    LocalDate endDate,
    TripStatus status,
    List<Concern> concerns,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
