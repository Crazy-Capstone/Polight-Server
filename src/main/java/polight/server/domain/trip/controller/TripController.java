package polight.server.domain.trip.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import polight.server.domain.trip.dto.TripDto;
import polight.server.domain.trip.entity.TripStatus;
import polight.server.domain.trip.service.TripService;
import polight.server.global.api.ApiResponse;
import polight.server.global.config.OpenApiConfig;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trips")
@Tag(name = "Trip", description = "여행 조회 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class TripController {
  private final TripService tripService;

  @GetMapping("/current")
  @Operation(summary = "현재 여행 조회", description = "ACTIVE 여행을 우선하고, 없으면 오늘이 여행 기간인 여행을 반환합니다.")
  public ApiResponse<TripDto.Response> current(@AuthenticationPrincipal UUID userId) {
    return ApiResponse.ok(tripService.getCurrent(userId));
  }

  @GetMapping
  @Operation(summary = "내 여행 목록 조회")
  public ApiResponse<List<TripDto.Response>> list(@AuthenticationPrincipal UUID userId,
      @Parameter(description = "생략하면 전체 조회") @RequestParam(required = false) TripStatus status) {
    return ApiResponse.ok(tripService.getTrips(userId, status));
  }
}
