package polight.server.domain.policy.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import polight.server.domain.policy.dto.PolicyDto;
import polight.server.domain.policy.entity.PolicyStatus;
import polight.server.domain.policy.service.PolicyService;
import polight.server.global.config.OpenApiConfig;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/policies")
@Tag(name = "Policy", description = "보험 및 보장 조회 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class PolicyController {
  private final PolicyService policyService;

  @GetMapping("/current")
  @Operation(summary = "현재 보험 조회")
  public polight.server.global.api.ApiResponse<PolicyDto.Response> current(@AuthenticationPrincipal UUID userId) {
    return polight.server.global.api.ApiResponse.ok(policyService.getCurrent(userId));
  }

  @GetMapping
  @Operation(summary = "내 보험 목록 조회")
  public polight.server.global.api.ApiResponse<List<PolicyDto.Response>> list(
      @AuthenticationPrincipal UUID userId,
      @Parameter(description = "생략하면 전체 조회") @RequestParam(required = false) PolicyStatus status) {
    return polight.server.global.api.ApiResponse.ok(policyService.getPolicies(userId, status));
  }

  @GetMapping("/{policyId}")
  @Operation(summary = "보험 상세 조회")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "조회 성공"),
      @ApiResponse(responseCode = "404", description = "보험이 없거나 소유자가 아님")})
  public polight.server.global.api.ApiResponse<PolicyDto.Response> detail(
      @AuthenticationPrincipal UUID userId,
      @Parameter(description = "보험 UUID", required = true) @PathVariable UUID policyId) {
    return polight.server.global.api.ApiResponse.ok(policyService.getPolicy(userId, policyId));
  }

  @GetMapping("/{policyId}/coverages")
  @Operation(summary = "보험 보장 항목 목록 조회")
  public polight.server.global.api.ApiResponse<PolicyDto.CoverageListResponse> coverages(
      @AuthenticationPrincipal UUID userId, @PathVariable UUID policyId) {
    return polight.server.global.api.ApiResponse.ok(policyService.getCoverages(userId, policyId));
  }

  @GetMapping("/{policyId}/coverages/{coverageId}")
  @Operation(summary = "보험 보장 상세 조회", description = "한도 요약, 상세 조건, 필요 서류를 포함합니다.")
  public polight.server.global.api.ApiResponse<PolicyDto.CoverageDetailResponse> coverage(
      @AuthenticationPrincipal UUID userId, @PathVariable UUID policyId,
      @PathVariable UUID coverageId) {
    return polight.server.global.api.ApiResponse.ok(policyService.getCoverage(userId, policyId, coverageId));
  }
}
