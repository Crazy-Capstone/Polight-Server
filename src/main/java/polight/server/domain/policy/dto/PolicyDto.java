package polight.server.domain.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import polight.server.domain.analysis.entity.CoverageStatus;
import polight.server.domain.policy.entity.PolicyStatus;

public final class PolicyDto {
  private PolicyDto() {}

  @Schema(name = "PolicyTrip")
  public record TripInfo(UUID id, String title, String countryCode, String countryName,
                         String cityName, String flagEmoji, LocalDate startDate, LocalDate endDate) {}

  @Schema(name = "PolicyResponse")
  public record Response(
      UUID id, UUID tripId,
      @Schema(example = "일본 여행자 보험 (삼성화재)") String displayName,
      @Schema(example = "삼성화재") String insurerName,
      @Schema(example = "해외여행보험") String productName,
      @Schema(description = "암호문을 노출하지 않는 표시값", example = "POL-****") String policyNumberMasked,
      LocalDate startDate, LocalDate endDate, String dDayLabel, PolicyStatus status,
      Integer coverageScore, long coverageCount, TripInfo trip) {}

  @Schema(name = "CoverageSummary")
  public record CoverageSummary(UUID id, String emoji, String title, String subtitle,
                                String limitLabel, boolean isCovered, CoverageStatus coverageStatus,
                                Long limitAmount, String limitCurrency, int sortOrder) {}

  @Schema(name = "CoverageListResponse")
  public record CoverageListResponse(TripInfo trip, PolicyBrief policy,
                                     List<CoverageSummary> coverages) {}

  public record PolicyBrief(UUID id, String insurerName, String displayName) {}

  @Schema(name = "CoverageLimitItem")
  public record LimitItem(UUID id, String label, String value, Long limitAmount,
                          String limitCurrency, String description) {}

  @Schema(name = "CoverageDetailItemResponse")
  public record DetailItem(UUID id, String title, String subtitle, boolean isCovered) {}

  @Schema(name = "RequiredDocumentResponse")
  public record RequiredDocumentItem(UUID id, String documentName, boolean mandatory) {}

  @Schema(name = "CoverageDetailResponse")
  public record CoverageDetailResponse(
      UUID id, String emoji, String title, String subtitle, String insurerName,
      String limitLabel, boolean isCovered, CoverageStatus coverageStatus,
      Long limitAmount, String limitCurrency, String conditions,
      List<LimitItem> summaryItems, List<DetailItem> detailItems,
      List<RequiredDocumentItem> requiredDocuments) {}
}
