package polight.server.domain.trip.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.trip.entity.TripStatus;

public record TripResponse(
    UUID id,
    String name,
    LocalDate startDate,
    LocalDate endDate,
    TripStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static TripResponse from(Trip trip) {
    return new TripResponse(
        trip.getId(),
        trip.getName(),
        trip.getStartDate(),
        trip.getEndDate(),
        trip.getStatus(),
        trip.getCreatedAt(),
        trip.getUpdatedAt());
  }
}
