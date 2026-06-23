package polight.server.domain.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import polight.server.domain.analysis.entity.CoverageDetailItem;
import polight.server.domain.analysis.entity.CoverageItem;
import polight.server.domain.analysis.entity.CoverageStatus;
import polight.server.domain.analysis.entity.RequiredDocument;
import polight.server.domain.analysis.entity.SubCoverageLimit;
import polight.server.domain.analysis.repository.CoverageDetailItemRepository;
import polight.server.domain.analysis.repository.CoverageItemRepository;
import polight.server.domain.analysis.repository.RequiredDocumentRepository;
import polight.server.domain.analysis.repository.SubCoverageLimitRepository;
import polight.server.domain.policy.entity.Policy;
import polight.server.domain.policy.entity.PolicyStatus;
import polight.server.domain.policy.repository.PolicyRepository;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.trip.entity.TripStatus;
import polight.server.domain.user.entity.User;
import polight.server.global.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {
  @Mock PolicyRepository policyRepository;
  @Mock CoverageItemRepository coverageRepository;
  @Mock SubCoverageLimitRepository limitRepository;
  @Mock CoverageDetailItemRepository detailRepository;
  @Mock RequiredDocumentRepository documentRepository;
  private PolicyService policyService;
  private UUID userId;
  private Policy policy;

  @BeforeEach
  void setUp() {
    policyService = new PolicyService(policyRepository, coverageRepository, limitRepository,
        detailRepository, documentRepository,
        Clock.fixed(Instant.parse("2026-06-23T00:00:00Z"), ZoneOffset.UTC));
    userId = UUID.randomUUID();
    User user = User.builder().name("류지").provider(User.Provider.KAKAO).providerId("k").build();
    ReflectionTestUtils.setField(user, "id", userId);
    Trip trip = Trip.builder().user(user).title("일본 여행").countryCode("JP").countryName("일본")
        .startDate(LocalDate.of(2026, 6, 23)).endDate(LocalDate.of(2026, 6, 30))
        .status(TripStatus.ACTIVE).build();
    ReflectionTestUtils.setField(trip, "id", UUID.randomUUID());
    policy = Policy.builder().user(user).trip(trip).insurerName("삼성화재").productName("여행보험")
        .displayName("일본 여행보험").startDate(trip.getStartDate()).endDate(trip.getEndDate())
        .status(PolicyStatus.ACTIVE).coverageScore(92).build();
    ReflectionTestUtils.setField(policy, "id", UUID.randomUUID());
  }

  @Test
  void currentPolicy_returnsActivePolicy() {
    given(policyRepository.findFirstByUserIdAndStatusOrderByStartDateDesc(userId, PolicyStatus.ACTIVE))
        .willReturn(Optional.of(policy));
    given(coverageRepository.countByPolicyId(policy.getId())).willReturn(6L);

    var response = policyService.getCurrent(userId);

    assertThat(response.id()).isEqualTo(policy.getId());
    assertThat(response.coverageCount()).isEqualTo(6);
  }

  @Test
  void policyAccess_isBlockedForOtherUser() {
    UUID policyId = UUID.randomUUID();
    given(policyRepository.findByIdAndUserId(policyId, userId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> policyService.getPolicy(userId, policyId))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void coverageDetail_mapsLimitsDetailsAndRequiredDocuments() {
    UUID coverageId = UUID.randomUUID();
    CoverageItem coverage = CoverageItem.builder().policy(policy).emoji("🏥").title("의료비")
        .subtitle("입원·통원").limitLabel("최대 1억원").covered(true)
        .coverageStatus(CoverageStatus.COVERED).limitAmount(100_000_000L).limitCurrency("KRW").build();
    ReflectionTestUtils.setField(coverage, "id", coverageId);
    SubCoverageLimit limit = SubCoverageLimit.builder().coverageItem(coverage).label("입원")
        .value("최대 1억원").sortOrder(0).build();
    CoverageDetailItem detail = CoverageDetailItem.builder().coverageItem(coverage).title("외래 진료비")
        .subtitle("검사 포함").covered(true).sortOrder(0).build();
    RequiredDocument required = RequiredDocument.builder().coverageItem(coverage)
        .documentName("진단서").mandatory(true).sortOrder(0).build();
    given(policyRepository.findByIdAndUserId(policy.getId(), userId)).willReturn(Optional.of(policy));
    given(coverageRepository.findByIdAndPolicyId(coverageId, policy.getId())).willReturn(Optional.of(coverage));
    given(limitRepository.findByCoverageItemIdOrderBySortOrderAsc(coverageId)).willReturn(List.of(limit));
    given(detailRepository.findByCoverageItemIdOrderBySortOrderAsc(coverageId)).willReturn(List.of(detail));
    given(documentRepository.findByCoverageItemIdOrderBySortOrderAsc(coverageId)).willReturn(List.of(required));

    var response = policyService.getCoverage(userId, policy.getId(), coverageId);

    assertThat(response.summaryItems()).extracting("label").containsExactly("입원");
    assertThat(response.detailItems()).extracting("title").containsExactly("외래 진료비");
    assertThat(response.requiredDocuments()).extracting("documentName").containsExactly("진단서");
  }
}
