package polight.server.domain.analysis.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import polight.server.domain.analysis.dto.AnalysisResponse;
import polight.server.domain.analysis.dto.CoverageAnalysisResponse;
import polight.server.domain.analysis.service.AnalysisResultService;
import polight.server.domain.analysis.service.CoverageAnalysisService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trips/{tripId}/documents/{documentId}/analysis")
@Tag(name = "Analysis", description = "여행 보험 문서(증권·약관) 분석 API")
public class AnalysisResultController {

  private final AnalysisResultService analysisResultService;
  private final CoverageAnalysisService coverageAnalysisService;

  @PostMapping
  @Operation(
      summary = "보험 문서 분석 시작 (약관용)",
      description =
          """
          **증권은 이 API를 호출할 필요가 없습니다.** 업로드 시점에 분석이 자동으로 시작됩니다.

          증권 분석 결과에 필요한 약관을 DB에서 찾지 못해 사용자에게 받아 올린 경우, 그 약관의 분석을 시작할 때 씁니다.

          동일한 문서에 대한 중복 요청은 기존 분석 작업을 그대로 반환합니다. 재분석은 일어나지 않습니다.
          """)
  @ResponseStatus(HttpStatus.CREATED)
  public ResponseEntity<AnalysisResponse> startAnalysis(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID tripId,
      @PathVariable UUID documentId) {
    AnalysisResponse response = analysisResultService.startAnalysis(userId, tripId, documentId);
    return ResponseEntity.created(
            URI.create("/api/v1/trips/" + tripId + "/documents/" + documentId + "/analysis"))
        .body(response);
  }

  @GetMapping
  @Operation(summary = "보험 문서 분석 상태 및 결과 조회")
  public AnalysisResponse getAnalysis(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID tripId,
      @PathVariable UUID documentId) {
    return analysisResultService.getAnalysis(userId, tripId, documentId);
  }

  @GetMapping("/coverages")
  @Operation(
      summary = "보장 내역 조회",
      description =
          """
          여행에 저장된 걱정되는 상황에 해당하는 담보를 목록 위로 올려 내려줍니다.

          - `selectedConcerns[].covered` 가 false 이면 그 걱정에 해당하는 담보를 찾지 못한 것입니다.
            `coveragesComplete` 가 false 인 동안에는 "가입하지 않았다"로 단정할 수 없습니다.
          - `status` 가 COMPLETED 가 아니면 `coverages` 와 `selectedConcerns` 는 빈 목록입니다.
          """)
  public CoverageAnalysisResponse getCoverages(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID tripId,
      @PathVariable UUID documentId) {
    return coverageAnalysisService.getCoverages(userId, tripId, documentId);
  }
}
