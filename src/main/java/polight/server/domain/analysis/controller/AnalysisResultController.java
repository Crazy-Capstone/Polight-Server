package polight.server.domain.analysis.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import polight.server.domain.analysis.dto.AnalysisResponse;
import polight.server.domain.analysis.service.AnalysisResultService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trips/{tripId}/documents/{documentId}/analysis")
@Tag(name = "Analysis", description = "여행 약관 분석 API")
public class AnalysisResultController {

  private final AnalysisResultService analysisResultService;

  @PostMapping
  @Operation(summary = "약관 분석 시작", description = "동일한 문서에 대한 중복 요청은 기존 분석 작업을 반환합니다.")
  public ResponseEntity<AnalysisResponse> start(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID tripId,
      @PathVariable UUID documentId) {
    AnalysisResponse response = analysisResultService.start(userId, tripId, documentId);
    return ResponseEntity.created(
            URI.create(
                "/api/v1/trips/" + tripId + "/documents/" + documentId + "/analysis"))
        .body(response);
  }

  @GetMapping
  @Operation(summary = "약관 분석 상태 및 결과 조회")
  public AnalysisResponse find(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID tripId,
      @PathVariable UUID documentId) {
    return analysisResultService.find(userId, tripId, documentId);
  }
}
