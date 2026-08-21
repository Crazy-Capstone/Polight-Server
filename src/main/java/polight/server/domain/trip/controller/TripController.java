package polight.server.domain.trip.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import polight.server.domain.trip.dto.TripCreateRequest;
import polight.server.domain.trip.dto.TripResponse;
import polight.server.domain.trip.dto.TripUpdateRequest;
import polight.server.domain.trip.dto.TripWithDocumentResponse;
import polight.server.domain.trip.service.TripRegistrationService;
import polight.server.domain.trip.service.TripService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trips")
@Tag(name = "Trip", description = "여행 세션 API")
public class TripController {

  private final TripService tripService;
  private final TripRegistrationService tripRegistrationService;

  // 여행 생성은 이 엔드포인트 하나뿐이다. 증권 없는 여행은 분석할 대상이 없는 빈 레코드라
  // JSON 전용 생성 API를 두지 않는다. 문서를 추가로 올릴 때는 문서 업로드 API를 쓴다.
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "여행 세션 생성 + 보험 문서 업로드",
      description =
          """
          한 화면에서 문서 파일과 여행 정보를 함께 입력받아 한 번의 요청으로 처리합니다.
          두 작업은 같은 트랜잭션이라 문서 저장이 실패하면 여행도 만들어지지 않습니다.

          multipart 파트 구성:
          - `trip` : 여행 정보 JSON. 이 파트의 Content-Type을 `application/json` 으로 지정해야 합니다.
            파일 종류는 이 JSON 의 `documentKind` 필드로 지정하며, 생략하면 `CERTIFICATE`(증권) 입니다.
          - `file` : 업로드할 PDF 파일

          응답의 trip.id 와 document.id 로 이어서 분석 시작 API를 호출합니다.
          """)
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
          @Content(
              mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
              encoding =
                  @Encoding(name = "trip", contentType = MediaType.APPLICATION_JSON_VALUE)))
  @ResponseStatus(HttpStatus.CREATED)
  public ResponseEntity<TripWithDocumentResponse> createTripWithDocument(
      @AuthenticationPrincipal UUID userId,
      @Valid @RequestPart("trip") TripCreateRequest trip,
      @RequestPart("file") MultipartFile file) {
    TripWithDocumentResponse response =
        tripRegistrationService.createTripWithDocument(userId, trip, file);
    return ResponseEntity.created(URI.create("/api/v1/trips/" + response.trip().id()))
        .body(response);
  }

  @GetMapping
  @Operation(summary = "내 여행 세션 목록")
  public List<TripResponse> getTrips(@AuthenticationPrincipal UUID userId) {
    return tripService.getTrips(userId);
  }

  @GetMapping("/{tripId}")
  @Operation(summary = "여행 세션 조회")
  public TripResponse getTrip(@AuthenticationPrincipal UUID userId, @PathVariable UUID tripId) {
    return tripService.getTrip(userId, tripId);
  }

  @PatchMapping("/{tripId}")
  @Operation(summary = "여행 세션 정보 변경")
  public TripResponse updateTrip(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID tripId,
      @Valid @RequestBody TripUpdateRequest request) {
    return tripService.updateTrip(userId, tripId, request);
  }
}
