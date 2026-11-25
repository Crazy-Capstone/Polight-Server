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
  @Operation(summary = "동의 내역 저장 및 부분 갱신",
      description = "여러 화면에서 수집한 항목을 나누어 POST할 수 있습니다. 최초 저장 시 생략 항목은 null(미수집)이며, "
          + "기존 내역이 있으면 전달한 항목만 갱신하고 나머지는 유지합니다. 과거 변경 이력은 누적하지 않으며 "
          + "실제 값이 변경되면 updatedAt을 갱신합니다. 명시적 null과 빈 요청은 거부합니다.")
  @ApiResponse(responseCode = "201", description = "최초 생성된 동의 내역",
      content = @Content(schema = @Schema(implementation = ConsentResponse.class)))
  @ApiResponse(responseCode = "200", description = "기존 동의 내역 부분 갱신 또는 동일 값 재전송",
      content = @Content(schema = @Schema(implementation = ConsentResponse.class)))
  @ApiResponse(responseCode = "400", description = "INVALID_INPUT: 빈 요청, 명시적 null 또는 잘못된 동의 유형",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public ResponseEntity<ConsentResponse> save(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody ConsentCreateRequest request) {
    var result = service.save(userId, request);
    return result.created()
        ? ResponseEntity.created(URI.create("/api/consents")).body(result.response())
        : ResponseEntity.ok(result.response());
  }

  @PatchMapping
  @Operation(summary = "현재 동의 내역 부분 수정",
      description = "전달한 항목만 수집/변경하며 미수집(null) 항목도 true/false로 설정할 수 있습니다. "
          + "생략 항목은 유지하고 명시적 null과 빈 요청은 거부합니다. 과거 변경 이력은 누적하지 않으며 "
          + "실제 값이 변경되면 updatedAt을 갱신합니다. 동일 값 재전송은 수정 시각을 변경하지 않습니다. "
          + "접근 제한이나 회원 탈퇴는 수행하지 않습니다.")
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
      description = "7개 항목을 모두 반환합니다. true=동의, false=명시적 미동의, null=미수집입니다. "
          + "updatedAt은 전체 동의 상태의 마지막 변경 시각입니다. 최초 저장 전에는 CONSENT_NOT_FOUND(404)입니다.")
  @ApiResponse(responseCode = "200", description = "현재 동의 내역")
  @ApiResponse(responseCode = "404", description = "CONSENT_NOT_FOUND",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public ConsentResponse get(@AuthenticationPrincipal UUID userId) {
    return service.get(userId);
  }
}
