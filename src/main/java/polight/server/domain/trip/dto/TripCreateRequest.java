package polight.server.domain.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record TripCreateRequest(
    @NotBlank(message = "여행 이름은 필수입니다.")
        @Size(max = 100, message = "여행 이름은 100자 이하여야 합니다.")
        String name,
    @NotNull(message = "여행 시작일은 필수입니다.") LocalDate startDate,
    @NotNull(message = "여행 종료일은 필수입니다.") LocalDate endDate) {}
