package polight.server.domain.trip.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import polight.server.domain.trip.dto.TripCreateRequest;
import polight.server.domain.trip.dto.TripResponse;
import polight.server.domain.trip.dto.TripUpdateRequest;
import polight.server.domain.trip.service.TripService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trips")
@Tag(name = "Trip", description = "여행 세션 API")
public class TripController {

  private final TripService tripService;

  @PostMapping
  @Operation(summary = "여행 세션 생성", description = "사용자가 직접 입력한 이름으로 여행 세션을 생성합니다.")
  public ResponseEntity<TripResponse> create(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody TripCreateRequest request) {
    TripResponse response = tripService.create(userId, request);
    return ResponseEntity.created(URI.create("/api/v1/trips/" + response.id())).body(response);
  }

  @GetMapping
  @Operation(summary = "내 여행 세션 목록")
  public List<TripResponse> findAll(@AuthenticationPrincipal UUID userId) {
    return tripService.findAll(userId);
  }

  @GetMapping("/{tripId}")
  @Operation(summary = "여행 세션 조회")
  public TripResponse find(
      @AuthenticationPrincipal UUID userId, @PathVariable UUID tripId) {
    return tripService.find(userId, tripId);
  }

  @PatchMapping("/{tripId}")
  @Operation(summary = "여행 세션 정보 변경")
  public TripResponse update(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID tripId,
      @Valid @RequestBody TripUpdateRequest request) {
    return tripService.update(userId, tripId, request);
  }
}
