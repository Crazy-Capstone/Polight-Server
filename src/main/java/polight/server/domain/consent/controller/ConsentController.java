package polight.server.domain.consent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import polight.server.domain.consent.dto.ConsentCreateRequest;
import polight.server.domain.consent.dto.ConsentResponse;
import polight.server.domain.consent.dto.ConsentUpdateRequest;
import polight.server.domain.consent.service.ConsentService;
import polight.server.global.exception.ErrorResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/consents")
@Tag(name = "Consent", description = "인증된 사용자의 현재 동의 내역")
@ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED",
    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public class ConsentController {

  private final ConsentService service;

  @PostMapping
  @Operation(summary = "최초 동의 내역 저장",
      description = "7개 항목의 true/false를 모두 저장합니다. 로그인과 독립적이며 동의 값으로 화면 이동이나 가입을 제한하지 않습니다. "
          + "이미 저장된 경우 같은 요청을 다시 보내도 409입니다.")
  @ApiResponse(responseCode = "201", description = "저장된 동의 내역",
      content = @Content(schema = @Schema(implementation = ConsentResponse.class)))
  @ApiResponse(responseCode = "400", description = "INVALID_INPUT: 누락, null 또는 잘못된 동의 유형",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(responseCode = "409", description = "CONSENT_ALREADY_EXISTS: 최초 동의가 이미 존재",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public ResponseEntity<ConsentResponse> create(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody ConsentCreateRequest request) {
    return ResponseEntity.created(URI.create("/api/consents")).body(service.create(userId, request));
  }

  @PatchMapping
  @Operation(summary = "현재 동의 내역 부분 수정",
      description = "전달한 항목만 변경합니다. 누락 항목은 유지하고 null과 빈 요청은 거부합니다. "
          + "필수/선택 구분에 따른 접근 제한이나 회원 탈퇴는 수행하지 않습니다.")
  @ApiResponse(responseCode = "200", description = "변경된 동의 내역")
  @ApiResponse(responseCode = "400", description = "INVALID_INPUT",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(responseCode = "404", description = "CONSENT_NOT_FOUND: 최초 동의 저장 필요",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public ConsentResponse update(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody ConsentUpdateRequest request) {
    return service.update(userId, request);
  }

  @GetMapping
  @Operation(summary = "현재 동의 내역 조회",
      description = "저장된 7개 항목과 생성/변경 시각을 반환합니다. 최초 저장 전에는 CONSENT_NOT_FOUND(404)입니다.")
  @ApiResponse(responseCode = "200", description = "현재 동의 내역")
  @ApiResponse(responseCode = "404", description = "CONSENT_NOT_FOUND",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public ConsentResponse get(@AuthenticationPrincipal UUID userId) {
    return service.get(userId);
  }
}
