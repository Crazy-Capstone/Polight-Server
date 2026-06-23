package polight.server.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import polight.server.domain.notification.dto.NotificationDto;
import polight.server.domain.notification.entity.NotificationPreference;
import polight.server.domain.notification.repository.NotificationPreferenceRepository;
import polight.server.domain.notification.repository.NotificationRepository;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.service.UserService;
import polight.server.global.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
  @Mock NotificationRepository notificationRepository;
  @Mock NotificationPreferenceRepository preferenceRepository;
  @Mock UserService userService;
  private NotificationService notificationService;
  private UUID userId;
  private User user;

  @BeforeEach
  void setUp() {
    notificationService = new NotificationService(notificationRepository, preferenceRepository, userService);
    userId = UUID.randomUUID();
    user = User.builder().name("류지").provider(User.Provider.KAKAO).providerId("k").build();
  }

  @Test
  void markRead_blocksNotificationOwnedByAnotherUser() {
    UUID notificationId = UUID.randomUUID();
    given(notificationRepository.findByIdAndUserId(notificationId, userId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> notificationService.markRead(userId, notificationId))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void preference_getCreatesDefaults_andUpdateChangesProvidedValues() {
    given(preferenceRepository.findByUserId(userId)).willReturn(Optional.empty());
    given(userService.findUser(userId)).willReturn(user);
    given(preferenceRepository.save(any(NotificationPreference.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    var defaults = notificationService.getPreference(userId);
    assertThat(defaults.pushEnabled()).isTrue();

    NotificationPreference existing = NotificationPreference.builder().user(user).build();
    given(preferenceRepository.findByUserId(userId)).willReturn(Optional.of(existing));
    var updated = notificationService.updatePreference(userId,
        new NotificationDto.PreferenceRequest(null, null, false, false));

    assertThat(updated.analysisDoneEnabled()).isFalse();
    assertThat(updated.pushEnabled()).isFalse();
    assertThat(updated.policyExpiryEnabled()).isTrue();
  }
}
