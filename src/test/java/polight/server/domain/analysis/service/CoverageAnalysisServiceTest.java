package polight.server.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import polight.server.domain.analysis.dto.CoverageAnalysisResponse;
import polight.server.domain.analysis.dto.CoverageAnalysisResponse.CoverageItemResponse;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.analysis.entity.CoverageItem;
import polight.server.domain.analysis.entity.CoverageStatus;
import polight.server.domain.analysis.repository.AnalysisResultRepository;
import polight.server.domain.analysis.repository.CoverageItemRepository;
import polight.server.domain.concern.service.ConcernCoverageMatcher;
import polight.server.domain.insurance.entity.DocumentKind;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.insurance.service.PolicyDocumentService;
import polight.server.domain.terms.entity.ExclusionCondition;
import polight.server.domain.terms.entity.PolicyTerms;
import polight.server.domain.terms.entity.PolicyTermsCoverage;
import polight.server.domain.terms.entity.RequiredDocument;
import polight.server.domain.terms.repository.CoverageDetailItemRepository;
import polight.server.domain.terms.repository.ExclusionConditionRepository;
import polight.server.domain.terms.repository.RequiredDocumentRepository;
import polight.server.domain.terms.repository.SubCoverageLimitRepository;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.trip.service.TripService;

/** 보장 상세가 가입 사실(coverage_items)과 약관 사실(policy_terms_coverages)을 합쳐 내려주는지. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CoverageAnalysisServiceTest {

  @Mock private AnalysisResultRepository analysisResultRepository;
  @Mock private CoverageItemRepository coverageItemRepository;
  @Mock private CoverageDetailItemRepository coverageDetailItemRepository;
  @Mock private SubCoverageLimitRepository subCoverageLimitRepository;
  @Mock private RequiredDocumentRepository requiredDocumentRepository;
  @Mock private ExclusionConditionRepository exclusionConditionRepository;
  @Mock private PolicyDocumentService policyDocumentService;
  @Mock private TripService tripService;

  private CoverageAnalysisService service;
  private UUID userId;
  private UUID tripId;
  private UUID documentId;
  private AnalysisResult analysis;

  @BeforeEach
  void setUp() {
    service =
        new CoverageAnalysisService(
            analysisResultRepository,
            coverageItemRepository,
            coverageDetailItemRepository,
            subCoverageLimitRepository,
            requiredDocumentRepository,
            exclusionConditionRepository,
            policyDocumentService,
            tripService,
            new ConcernCoverageMatcher());

    userId = UUID.randomUUID();
    tripId = UUID.randomUUID();
    documentId = UUID.randomUUID();

    PolicyDocument document =
        PolicyDocument.builder()
            .originalFilename("증권.pdf")
            .storedFilePath("/증권.pdf")
            .documentKind(DocumentKind.CERTIFICATE)
            .build();
    analysis = AnalysisResult.builder().document(document).build();
    ReflectionTestUtils.setField(analysis, "id", UUID.randomUUID());
    analysis.completeWith(
        null, "{}", null, null, null, false, "삼성화재", "해외여행보험", null, null, LocalDateTime.now());

    Trip trip =
        Trip.builder()
            .name("도쿄")
            .startDate(LocalDate.of(2026, 3, 1))
            .endDate(LocalDate.of(2026, 3, 5))
            .build();

    given(policyDocumentService.getOwnedDocument(userId, tripId, documentId)).willReturn(document);
    given(tripService.getOwnedTrip(userId, tripId)).willReturn(trip);
    given(analysisResultRepository.findOneByDocumentId(documentId))
        .willReturn(Optional.of(analysis));

    givenNoChildren();
  }

  @Test
  void 약관_규칙이_연결된_담보는_면책과_서류를_함께_내려준다() {
    PolicyTerms terms = terms();
    PolicyTermsCoverage rule = rule(terms, "상해의료비", 0);
    CoverageItem item = item("해외여행중상해의료비", 30_000_000L, rule);

    given(coverageItemRepository.findByAnalysisResultIdOrderBySortOrderAsc(analysis.getId()))
        .willReturn(List.of(item));
    given(exclusionConditionRepository.findByTermsCoverageIdInOrderBySortOrderAsc(anyCollection()))
        .willReturn(List.of(exclusion(rule, "전문등반 중 상해")));
    given(requiredDocumentRepository.findByTermsCoverageIdInOrderBySortOrderAsc(anyCollection()))
        .willReturn(List.of(document(rule, "진단서")));

    CoverageItemResponse response = only(service.getCoverages(userId, tripId, documentId));

    // 가입 사실은 담보에서
    assertThat(response.limitAmount()).isEqualTo(30_000_000L);
    assertThat(response.isCovered()).isTrue();
    // 약관 사실은 규칙에서
    assertThat(response.exclusions()).singleElement().satisfies(e -> assertThat(e.title()).isEqualTo("전문등반 중 상해"));
    assertThat(response.requiredDocuments()).singleElement()
        .satisfies(d -> assertThat(d.documentName()).isEqualTo("진단서"));
  }

  @Test
  void 약관_규칙이_없는_담보는_가입_정보만_내려준다() {
    // 약관을 못 찾았거나 담보명이 어느 규칙과도 맞지 않은 경우다. 억지로 붙이는 대신 비워 둔다.
    CoverageItem item = item("듣도보도못한특약", 1_000_000L, null);
    given(coverageItemRepository.findByAnalysisResultIdOrderBySortOrderAsc(analysis.getId()))
        .willReturn(List.of(item));

    CoverageItemResponse response = only(service.getCoverages(userId, tripId, documentId));

    assertThat(response.limitAmount()).isEqualTo(1_000_000L);
    assertThat(response.exclusions()).isEmpty();
    assertThat(response.requiredDocuments()).isEmpty();
    assertThat(response.subLimits()).isEmpty();
    assertThat(response.detailItems()).isEmpty();
  }

  @Test
  void 같은_규칙을_가리키는_담보_둘은_같은_면책을_공유한다() {
    // 이 구조의 요점이다. 면책은 상품의 사실이라 담보마다 복제하지 않고 규칙에 한 벌만 둔다.
    PolicyTerms terms = terms();
    PolicyTermsCoverage rule = rule(terms, "상해의료비", 0);
    CoverageItem domestic = item("국내 상해의료비", 10_000_000L, rule);
    CoverageItem overseas = item("해외 상해의료비", 30_000_000L, rule);

    given(coverageItemRepository.findByAnalysisResultIdOrderBySortOrderAsc(analysis.getId()))
        .willReturn(List.of(domestic, overseas));
    given(exclusionConditionRepository.findByTermsCoverageIdInOrderBySortOrderAsc(anyCollection()))
        .willReturn(List.of(exclusion(rule, "전문등반 중 상해")));

    List<CoverageItemResponse> responses = service.getCoverages(userId, tripId, documentId).coverages();

    assertThat(responses).hasSize(2);
    assertThat(responses).allSatisfy(
        r -> assertThat(r.exclusions()).singleElement()
            .satisfies(e -> assertThat(e.title()).isEqualTo("전문등반 중 상해")));
    // 가입금액은 각자의 것이라 달라야 한다.
    assertThat(responses).extracting(CoverageItemResponse::limitAmount)
        .containsExactlyInAnyOrder(10_000_000L, 30_000_000L);
  }

  @Test
  void 규칙이_하나도_연결되지_않았으면_약관_쪽을_조회하지_않는다() {
    // 빈 IN 절로 쿼리 네 번 나가는 것을 막는다.
    given(coverageItemRepository.findByAnalysisResultIdOrderBySortOrderAsc(analysis.getId()))
        .willReturn(List.of(item("특약", 1L, null)));

    service.getCoverages(userId, tripId, documentId);

    org.mockito.Mockito.verifyNoInteractions(
        exclusionConditionRepository,
        requiredDocumentRepository,
        subCoverageLimitRepository,
        coverageDetailItemRepository);
  }

  private void givenNoChildren() {
    given(coverageDetailItemRepository.findByTermsCoverageIdInOrderBySortOrderAsc(anyCollection()))
        .willReturn(List.of());
    given(subCoverageLimitRepository.findByTermsCoverageIdInOrderBySortOrderAsc(anyCollection()))
        .willReturn(List.of());
    given(requiredDocumentRepository.findByTermsCoverageIdInOrderBySortOrderAsc(anyCollection()))
        .willReturn(List.of());
    given(exclusionConditionRepository.findByTermsCoverageIdInOrderBySortOrderAsc(anyCollection()))
        .willReturn(List.of());
  }

  private CoverageItemResponse only(CoverageAnalysisResponse response) {
    assertThat(response.coverages()).hasSize(1);
    return response.coverages().get(0);
  }

  private static PolicyTerms terms() {
    PolicyTerms terms = PolicyTerms.official("삼성화재", "해외여행보험", null, null);
    ReflectionTestUtils.setField(terms, "id", UUID.randomUUID());
    return terms;
  }

  private static PolicyTermsCoverage rule(PolicyTerms terms, String title, int sortOrder) {
    PolicyTermsCoverage rule =
        PolicyTermsCoverage.builder().terms(terms).title(title).sortOrder(sortOrder).build();
    ReflectionTestUtils.setField(rule, "id", UUID.randomUUID());
    return rule;
  }

  private CoverageItem item(String title, Long limitAmount, PolicyTermsCoverage rule) {
    CoverageItem item =
        CoverageItem.builder()
            .analysisResult(analysis)
            .title(title)
            .coverageStatus(CoverageStatus.COVERED)
            .covered(true)
            .limitAmount(limitAmount)
            .sortOrder(0)
            .build();
    ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
    item.linkTermsCoverage(rule);
    return item;
  }

  private static ExclusionCondition exclusion(PolicyTermsCoverage rule, String title) {
    return ExclusionCondition.builder().termsCoverage(rule).title(title).sortOrder(0).build();
  }

  private static RequiredDocument document(PolicyTermsCoverage rule, String name) {
    return RequiredDocument.builder()
        .termsCoverage(rule)
        .documentName(name)
        .mandatory(true)
        .sortOrder(0)
        .build();
  }
}
