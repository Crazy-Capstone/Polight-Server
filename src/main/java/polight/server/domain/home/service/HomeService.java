package polight.server.domain.home.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.analysis.repository.CoverageItemRepository;
import polight.server.domain.home.dto.HomeSummaryResponse;
import polight.server.domain.notification.service.NotificationService;
import polight.server.domain.policy.dto.PolicyDto;
import polight.server.domain.policy.entity.Policy;
import polight.server.domain.policy.service.PolicyService;
import polight.server.domain.trip.service.TripService;
import polight.server.domain.user.service.UserService;

@Service
@RequiredArgsConstructor
public class HomeService {
  private final UserService userService;
  private final TripService tripService;
  private final PolicyService policyService;
  private final CoverageItemRepository coverageRepository;
  private final NotificationService notificationService;

  @Transactional(readOnly = true)
  public HomeSummaryResponse summary(UUID userId) {
    Policy current = policyService.findCurrentEntity(userId).orElse(null);
    PolicyDto.Response policy = current == null ? null : policyService.toResponse(current);
    List<PolicyDto.CoverageSummary> coverages = current == null ? List.of()
        : coverageRepository.findTop4ByPolicyIdOrderBySortOrderAsc(current.getId()).stream()
            .map(policyService::toCoverageSummary).toList();
    long unread = notificationService.unreadCount(userId);
    return new HomeSummaryResponse(userService.getMe(userId), tripService.getCurrent(userId), policy,
        current == null ? null : current.getCoverageScore(),
        current == null ? 0 : coverageRepository.countByPolicyId(current.getId()), coverages,
        unread > 0, unread);
  }
}
