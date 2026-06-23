package polight.server.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;
import polight.server.domain.notification.entity.NotificationType;

public final class NotificationDto {
  private NotificationDto() {}

  @Schema(name = "NotificationResponse")
  public record Response(UUID id, NotificationType type, String title, String body,
                         String deepLink, LocalDateTime readAt, LocalDateTime createdAt) {}

  @Schema(name = "NotificationPreferenceResponse")
  public record PreferenceResponse(boolean policyExpiryEnabled, boolean renewalEnabled,
                                   boolean analysisDoneEnabled, boolean pushEnabled) {}

  @Schema(name = "UpdateNotificationPreferenceRequest")
  public record PreferenceRequest(
      @Schema(example = "true") Boolean policyExpiryEnabled,
      @Schema(example = "true") Boolean renewalEnabled,
      @Schema(example = "true") Boolean analysisDoneEnabled,
      @Schema(example = "true") Boolean pushEnabled) {}
}
