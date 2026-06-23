package polight.server.domain.mypage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import polight.server.domain.notification.dto.NotificationDto;
import polight.server.domain.policy.dto.PolicyDto;
import polight.server.domain.trip.dto.TripDto;
import polight.server.domain.user.dto.UserDto;

@Schema(description = "마이페이지 조합 응답")
public record MyPageSummaryResponse(
    UserDto.Response user,
    Stats stats,
    TripDto.Response currentTrip,
    PolicyDto.Response currentPolicy,
    NotificationDto.PreferenceResponse notificationPreference) {
  public record Stats(long policyCount, long currentCoverageCount) {}
}
