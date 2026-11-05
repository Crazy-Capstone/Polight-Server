package polight.server.domain.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
    LocalDateTime updatedAt,
    @Schema(description = "선택 순서대로 정렬된 ISO 3166-1 alpha-2 대문자 국가코드 목록. 국가 정보가 없으면 빈 배열입니다.",
        example = "[\"JP\",\"KR\"]")
    List<String> countries
) {}
