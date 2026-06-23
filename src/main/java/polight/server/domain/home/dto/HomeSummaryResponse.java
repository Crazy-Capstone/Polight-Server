package polight.server.domain.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import polight.server.domain.policy.dto.PolicyDto;
import polight.server.domain.trip.dto.TripDto;
import polight.server.domain.user.dto.UserDto;

@Schema(description = "홈 화면 조합 응답")
public record HomeSummaryResponse(
    UserDto.Response user,
    TripDto.Response currentTrip,
    PolicyDto.Response currentPolicy,
    Integer coverageScore,
    long coverageCount,
    @Schema(description = "대표 보장 항목, 최대 4개") List<PolicyDto.CoverageSummary> coverageSummary,
    boolean hasUnreadNotification,
    long unreadNotificationCount) {}
