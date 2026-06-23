package polight.server.domain.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import polight.server.domain.notification.dto.NotificationDto;
import polight.server.domain.notification.service.NotificationService;
import polight.server.global.api.ApiResponse;
import polight.server.global.api.PageResponse;
import polight.server.global.config.OpenApiConfig;
import org.springframework.validation.annotation.Validated;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "Notification", description = "알림 및 알림 설정 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class NotificationController {
  private final NotificationService notificationService;

  @GetMapping("/notifications")
  @Operation(summary = "알림 목록 조회", description = "최신순 페이징 목록입니다.")
  public ApiResponse<PageResponse<NotificationDto.Response>> list(@AuthenticationPrincipal UUID userId,
      @Parameter(example = "0") @Min(0) @RequestParam(defaultValue = "0") int page,
      @Parameter(example = "20") @Min(1) @Max(100) @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(notificationService.list(userId, page, size));
  }

  @PatchMapping("/notifications/{notificationId}/read")
  @Operation(summary = "알림 읽음 처리")
  public ApiResponse<NotificationDto.Response> read(@AuthenticationPrincipal UUID userId,
      @PathVariable UUID notificationId) {
    return ApiResponse.ok(notificationService.markRead(userId, notificationId));
  }

  @GetMapping("/notification-preferences")
  @Operation(summary = "알림 설정 조회", description = "설정이 없으면 모두 활성화된 기본 설정을 생성합니다.")
  public ApiResponse<NotificationDto.PreferenceResponse> preference(@AuthenticationPrincipal UUID userId) {
    return ApiResponse.ok(notificationService.getPreference(userId));
  }

  @PatchMapping("/notification-preferences")
  @Operation(summary = "알림 설정 수정")
  public ApiResponse<NotificationDto.PreferenceResponse> updatePreference(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody NotificationDto.PreferenceRequest request) {
    return ApiResponse.ok(notificationService.updatePreference(userId, request));
  }
}
