package polight.server.domain.consent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import polight.server.domain.consent.dto.ConsentChangeRequest;
import polight.server.domain.consent.dto.ConsentStatusResponse;
import polight.server.domain.consent.service.ConsentService;
import polight.server.domain.user.service.UserService;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;
import polight.server.global.exception.ErrorResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me/consents")
@Tag(name = "Consent", description = "내 동의 상태 조회 및 변경")
public class ConsentController {

  private final ConsentService consentService;
  private final UserService userService;

  @GetMapping
  @Operation(summary = "내 동의 상태 조회",
      description = "이력이 없거나 철회됐거나 구버전인 항목은 false입니다.")
  @ApiResponse(responseCode = "200", description = "현재 동의 상태")
  @ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public ConsentStatusResponse getStatus(@AuthenticationPrincipal UUID userId) {
    return consentService.getStatus(userService.getUser(authenticatedId(userId)));
  }

  @PostMapping
  @Operation(summary = "내 동의 변경",
      description = "동의와 선택 항목 철회를 이력에 추가합니다. 필수 항목 철회는 지원하지 않습니다. "
          + "같은 요청을 반복 제출해도 각각 이력이 남습니다.")
  @ApiResponse(responseCode = "200", description = "저장 후 동의 상태")
  @ApiResponse(responseCode = "400",
      description = "INVALID_INPUT 또는 REQUIRED_CONSENT_WITHDRAWAL_NOT_SUPPORTED",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public ConsentStatusResponse changeConsents(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody ConsentChangeRequest request) {
    return consentService.changeConsents(userService.getUser(authenticatedId(userId)), request.decisions());
  }

  private UUID authenticatedId(UUID userId) {
    if (userId == null) {
      throw new BaseException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
    return userId;
  }
}
