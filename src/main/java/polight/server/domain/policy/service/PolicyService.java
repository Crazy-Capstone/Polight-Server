package polight.server.domain.policy.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.analysis.entity.CoverageItem;
import polight.server.domain.analysis.repository.CoverageDetailItemRepository;
import polight.server.domain.analysis.repository.CoverageItemRepository;
import polight.server.domain.analysis.repository.RequiredDocumentRepository;
import polight.server.domain.analysis.repository.SubCoverageLimitRepository;
import polight.server.domain.policy.dto.PolicyDto;
import polight.server.domain.policy.entity.Policy;
import polight.server.domain.policy.entity.PolicyStatus;
import polight.server.domain.policy.repository.PolicyRepository;
import polight.server.domain.trip.entity.Trip;
import polight.server.global.exception.BusinessException;
import polight.server.global.exception.ErrorCode;
import polight.server.global.util.DisplayUtils;

@Service
@RequiredArgsConstructor
public class PolicyService {
  private final PolicyRepository policyRepository;
  private final CoverageItemRepository coverageItemRepository;
  private final SubCoverageLimitRepository subCoverageLimitRepository;
  private final CoverageDetailItemRepository coverageDetailItemRepository;
  private final RequiredDocumentRepository requiredDocumentRepository;
  private final Clock clock;

  @Transactional(readOnly = true)
  public PolicyDto.Response getCurrent(UUID userId) {
    return findCurrentEntity(userId).map(this::toResponse).orElse(null);
  }

  @Transactional(readOnly = true)
  public Optional<Policy> findCurrentEntity(UUID userId) {
    LocalDate today = LocalDate.now(clock);
    return policyRepository.findFirstByUserIdAndStatusOrderByStartDateDesc(userId, PolicyStatus.ACTIVE)
        .or(() -> policyRepository
            .findFirstByUserIdAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
                userId, today, today));
  }

  @Transactional(readOnly = true)
  public List<PolicyDto.Response> getPolicies(UUID userId, PolicyStatus status) {
    List<Policy> policies = status == null ? policyRepository.findByUserId(userId)
        : policyRepository.findByUserIdAndStatus(userId, status);
    return policies.stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public PolicyDto.Response getPolicy(UUID userId, UUID policyId) {
    return toResponse(requireOwnedPolicy(userId, policyId));
  }

  @Transactional(readOnly = true)
  public PolicyDto.CoverageListResponse getCoverages(UUID userId, UUID policyId) {
    Policy policy = requireOwnedPolicy(userId, policyId);
    List<PolicyDto.CoverageSummary> coverages = coverageItemRepository
        .findByPolicyIdOrderBySortOrderAsc(policyId).stream().map(this::toCoverageSummary).toList();
    return new PolicyDto.CoverageListResponse(toTrip(policy.getTrip()),
        new PolicyDto.PolicyBrief(policy.getId(), policy.getInsurerName(), policy.getDisplayName()), coverages);
  }

  @Transactional(readOnly = true)
  public PolicyDto.CoverageDetailResponse getCoverage(UUID userId, UUID policyId, UUID coverageId) {
    Policy policy = requireOwnedPolicy(userId, policyId);
    CoverageItem coverage = coverageItemRepository.findByIdAndPolicyId(coverageId, policyId)
        .orElseThrow(() -> new BusinessException(ErrorCode.COVERAGE_NOT_FOUND));
    var limits = subCoverageLimitRepository.findByCoverageItemIdOrderBySortOrderAsc(coverageId)
        .stream().map(item -> new PolicyDto.LimitItem(item.getId(), item.getLabel(), item.getValue(),
            item.getLimitAmount(), item.getLimitCurrency(), item.getDescription())).toList();
    var details = coverageDetailItemRepository.findByCoverageItemIdOrderBySortOrderAsc(coverageId)
        .stream().map(item -> new PolicyDto.DetailItem(item.getId(), item.getTitle(),
            item.getSubtitle(), item.isCovered())).toList();
    var documents = requiredDocumentRepository.findByCoverageItemIdOrderBySortOrderAsc(coverageId)
        .stream().map(item -> new PolicyDto.RequiredDocumentItem(item.getId(), item.getDocumentName(),
            item.isMandatory())).toList();
    return new PolicyDto.CoverageDetailResponse(coverage.getId(), coverage.getEmoji(), coverage.getTitle(),
        coverage.getSubtitle(), policy.getInsurerName(), coverage.getLimitLabel(), coverage.isCovered(),
        coverage.getCoverageStatus(), coverage.getLimitAmount(), coverage.getLimitCurrency(),
        coverage.getConditions(), limits, details, documents);
  }

  public Policy requireOwnedPolicy(UUID userId, UUID policyId) {
    return policyRepository.findByIdAndUserId(policyId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.POLICY_NOT_FOUND));
  }

  public PolicyDto.Response toResponse(Policy policy) {
    long coverageCount = coverageItemRepository.countByPolicyId(policy.getId());
    String masked = policy.getPolicyNumberEncrypted() == null ? null : "POL-****";
    return new PolicyDto.Response(policy.getId(), policy.getTrip().getId(), policy.getDisplayName(),
        policy.getInsurerName(), policy.getProductName(), masked, policy.getStartDate(), policy.getEndDate(),
        DisplayUtils.dDayLabel(policy.getStartDate(), LocalDate.now(clock)), policy.getStatus(),
        policy.getCoverageScore(), coverageCount, toTrip(policy.getTrip()));
  }

  public PolicyDto.CoverageSummary toCoverageSummary(CoverageItem item) {
    return new PolicyDto.CoverageSummary(item.getId(), item.getEmoji(), item.getTitle(), item.getSubtitle(),
        item.getLimitLabel(), item.isCovered(), item.getCoverageStatus(), item.getLimitAmount(),
        item.getLimitCurrency(), item.getSortOrder());
  }

  private PolicyDto.TripInfo toTrip(Trip trip) {
    return new PolicyDto.TripInfo(trip.getId(), trip.getTitle(), trip.getCountryCode(),
        trip.getCountryName(), trip.getCityName(), trip.getFlagEmoji(), trip.getStartDate(), trip.getEndDate());
  }
}
