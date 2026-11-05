package polight.server.domain.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import polight.server.domain.trip.validation.CountryCode;

public record TripUpdateRequest(
    @NotBlank(message = "여행 이름은 필수입니다.")
        @Size(max = 100, message = "여행 이름은 100자 이하여야 합니다.")
        String name,
    @NotNull(message = "여행 시작일은 필수입니다.") LocalDate startDate,
    @NotNull(message = "여행 종료일은 필수입니다.") LocalDate endDate,
    @Schema(
        description = "ISO 3166-1 alpha-2 대문자 국가코드 목록. 전달하면 중복을 제거하고 선택 순서대로 교체합니다. 생략하거나 null이면 기존 국가를 유지하고, []이면 비웁니다.",
        example = "[\"JP\",\"KR\"]")
    List<@CountryCode String> countries) {}
