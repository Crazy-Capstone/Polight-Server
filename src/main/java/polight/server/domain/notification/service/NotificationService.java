package polight.server.domain.notification.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.notification.dto.NotificationDto;
import polight.server.domain.notification.entity.Notification;
import polight.server.domain.notification.entity.NotificationPreference;
import polight.server.domain.notification.repository.NotificationPreferenceRepository;
import polight.server.domain.notification.repository.NotificationRepository;
import polight.server.domain.user.service.UserService;
import polight.server.global.api.PageResponse;
import polight.server.global.exception.BusinessException;
import polight.server.global.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class NotificationService {
  private final NotificationRepository notificationRepository;
  private final NotificationPreferenceRepository preferenceRepository;
  private final UserService userService;

  @Transactional(readOnly = true)
  public PageResponse<NotificationDto.Response> list(UUID userId, int page, int size) {
    var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    return PageResponse.from(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
        .map(this::toResponse));
  }

  @Transactional
  public NotificationDto.Response markRead(UUID userId, UUID notificationId) {
    Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
    notification.markAsRead(null);
    return toResponse(notification);
  }

  @Transactional
  public NotificationDto.PreferenceResponse getPreference(UUID userId) {
    return toPreference(getOrCreate(userId));
  }

  @Transactional
  public NotificationDto.PreferenceResponse updatePreference(UUID userId,
      NotificationDto.PreferenceRequest request) {
    NotificationPreference preference = getOrCreate(userId);
    preference.update(request.policyExpiryEnabled(), request.renewalEnabled(),
        request.analysisDoneEnabled(), request.pushEnabled());
    return toPreference(preference);
  }

  @Transactional(readOnly = true)
  public long unreadCount(UUID userId) {
    return notificationRepository.countByUserIdAndReadAtIsNull(userId);
  }

  private NotificationPreference getOrCreate(UUID userId) {
    return preferenceRepository.findByUserId(userId).orElseGet(() ->
        preferenceRepository.save(NotificationPreference.builder().user(userService.findUser(userId)).build()));
  }

  private NotificationDto.Response toResponse(Notification item) {
    return new NotificationDto.Response(item.getId(), item.getType(), item.getTitle(), item.getBody(),
        item.getDeepLink(), item.getReadAt(), item.getCreatedAt());
  }

  private NotificationDto.PreferenceResponse toPreference(NotificationPreference item) {
    return new NotificationDto.PreferenceResponse(item.isPolicyExpiryEnabled(), item.isRenewalEnabled(),
        item.isAnalysisDoneEnabled(), item.isPushEnabled());
  }
}
