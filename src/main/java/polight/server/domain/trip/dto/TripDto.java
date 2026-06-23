package polight.server.domain.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;
import polight.server.domain.trip.entity.TripStatus;

public final class TripDto {
  private TripDto() {}

  @Schema(name = "TripResponse")
  public record Response(
      @Schema(example = "7c9fd445-f43b-477a-a3eb-5ff0d7fbfbd6") UUID id,
      @Schema(example = "일본 여행") String title,
      @Schema(example = "JP") String countryCode,
      @Schema(example = "일본") String countryName,
      @Schema(example = "도쿄") String cityName,
      @Schema(example = "🇯🇵") String flagEmoji,
      @Schema(example = "2026-06-23") LocalDate startDate,
      @Schema(example = "2026-06-30") LocalDate endDate,
      @Schema(example = "D-7") String dDayLabel,
      @Schema(example = "ACTIVE") TripStatus status) {}
}
