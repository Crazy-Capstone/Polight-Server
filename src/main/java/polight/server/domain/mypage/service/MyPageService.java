package polight.server.domain.mypage.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.analysis.repository.CoverageItemRepository;
import polight.server.domain.mypage.dto.MyPageSummaryResponse;
import polight.server.domain.notification.service.NotificationService;
import polight.server.domain.policy.entity.Policy;
import polight.server.domain.policy.repository.PolicyRepository;
import polight.server.domain.policy.service.PolicyService;
import polight.server.domain.trip.service.TripService;
import polight.server.domain.user.service.UserService;

@Service
@RequiredArgsConstructor
public class MyPageService {
  private final UserService userService;
  private final TripService tripService;
  private final PolicyService policyService;
  private final PolicyRepository policyRepository;
  private final CoverageItemRepository coverageRepository;
  private final NotificationService notificationService;

  @Transactional
  public MyPageSummaryResponse summary(UUID userId) {
    Policy current = policyService.findCurrentEntity(userId).orElse(null);
    long coverageCount = current == null ? 0 : coverageRepository.countByPolicyId(current.getId());
    return new MyPageSummaryResponse(userService.getMe(userId),
        new MyPageSummaryResponse.Stats(policyRepository.countByUserId(userId), coverageCount),
        tripService.getCurrent(userId), current == null ? null : policyService.toResponse(current),
        notificationService.getPreference(userId));
  }
}
