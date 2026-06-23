package polight.server.domain.insurance.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import polight.server.domain.insurance.service.PolicyDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import polight.server.domain.insurance.dto.PolicyDocumentDto;
import polight.server.global.api.ApiResponse;
import polight.server.global.config.OpenApiConfig;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/policy-documents")
@Tag(name = "Policy Document", description = "보험 문서 업로드 및 분석 상태 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class PolicyDocumentController {

  private final PolicyDocumentService policyDocumentService;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "보험 문서 업로드", description = "최대 10MB PDF 파일을 UUID 기반 파일명으로 저장합니다.")
  @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
      schema = @Schema(type = "object")))
  public ApiResponse<PolicyDocumentDto.Response> upload(@AuthenticationPrincipal UUID userId,
      @Parameter(description = "PDF 파일", required = true) @RequestPart("file") MultipartFile file,
      @RequestParam(required = false) UUID tripId, @RequestParam(required = false) UUID policyId) {
    return ApiResponse.ok(policyDocumentService.upload(userId, file, tripId, policyId));
  }

  @GetMapping("/{documentId}")
  @Operation(summary = "보험 문서 조회")
  public ApiResponse<PolicyDocumentDto.Response> get(@AuthenticationPrincipal UUID userId,
      @PathVariable UUID documentId) {
    return ApiResponse.ok(policyDocumentService.get(userId, documentId));
  }

  @PostMapping("/{documentId}/analyze")
  @Operation(summary = "보험 문서 분석 요청", description = "PROCESSING 상태와 분석 작업만 생성하며 가짜 완료 결과는 만들지 않습니다.")
  public ApiResponse<PolicyDocumentDto.AnalysisRequest> analyze(@AuthenticationPrincipal UUID userId,
      @PathVariable UUID documentId) {
    return ApiResponse.ok(policyDocumentService.requestAnalysis(userId, documentId));
  }

  @GetMapping("/{documentId}/analysis")
  @Operation(summary = "보험 문서 분석 상태 조회")
  public ApiResponse<PolicyDocumentDto.AnalysisResponse> analysis(@AuthenticationPrincipal UUID userId,
      @PathVariable UUID documentId) {
    return ApiResponse.ok(policyDocumentService.getAnalysis(userId, documentId));
  }
}
