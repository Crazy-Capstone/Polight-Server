package polight.server.domain.emergency.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import polight.server.domain.emergency.dto.EmergencyContactResponse;
import polight.server.domain.emergency.service.EmergencyContactService;
import polight.server.global.api.ApiResponse;
import polight.server.global.config.OpenApiConfig;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/emergency-contacts")
@Tag(name = "Emergency Contact", description = "국가·보험 긴급 연락처 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class EmergencyContactController {
  private final EmergencyContactService contactService;

  @GetMapping
  @Operation(summary = "긴급 연락처 조회", description = "국가 공통 연락처와 선택한 보험 연락처의 중복을 제거합니다.")
  public ApiResponse<List<EmergencyContactResponse>> list(@AuthenticationPrincipal UUID userId,
      @Parameter(required = true, example = "JP") @Pattern(regexp = "^[A-Za-z]{2}$")
          @RequestParam String countryCode,
      @Parameter(description = "선택 보험 UUID") @RequestParam(required = false) UUID policyId) {
    return ApiResponse.ok(contactService.getContacts(userId, countryCode, policyId));
  }
}
