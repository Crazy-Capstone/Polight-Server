package polight.server.domain.terms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.analysis.entity.CoverageItem;
import polight.server.domain.analysis.entity.CoverageStatus;
import polight.server.domain.insurance.entity.DocumentKind;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.terms.entity.PolicyTerms;
import polight.server.domain.terms.entity.PolicyTermsCoverage;
import polight.server.domain.terms.repository.PolicyTermsCoverageRepository;
import polight.server.domain.terms.service.CoverageTermsLinkSummary.UnlinkedCoverage;

@ExtendWith(MockitoExtension.class)
class CoverageTermsLinkerTest {

  @Mock private PolicyTermsCoverageRepository policyTermsCoverageRepository;

  private CoverageTermsLinker linker;
  private PolicyTerms terms;
  private AnalysisResult analysis;

  @BeforeEach
  void setUp() {
    linker = new CoverageTermsLinker(policyTermsCoverageRepository);
    terms = terms();
    analysis = analysisLinkedTo(terms);
  }

  @Test
  void 표기까지_같으면_붙인다() {
    PolicyTermsCoverage rule = rule("상해의료비", 0);
    givenRules(rule);
    CoverageItem item = item("상해 의료비");

    CoverageTermsLinkSummary summary = linker.link(analysis, List.of(item));

    assertThat(item.getTermsCoverage()).isSameAs(rule);
    assertThat(summary.exact()).isEqualTo(1);
    assertThat(summary.linked()).isEqualTo(1);
  }

  @Test
  void 증권이_수식어를_덧붙인_담보명도_붙인다() {
    // 증권은 보장내용 표에 적용 범위와 가입금액을 함께 인쇄한다. 이걸 못 맞추면 대부분의
    // 담보가 연결되지 않는다.
    PolicyTermsCoverage rule = rule("상해의료비", 0);
    givenRules(rule);
    CoverageItem item = item("해외여행중 상해의료비(3천만원)");

    CoverageTermsLinkSummary summary = linker.link(analysis, List.of(item));

    assertThat(item.getTermsCoverage()).isSameAs(rule);
    assertThat(summary.qualified()).isEqualTo(1);
  }

  @Test
  void 규칙명이_담보명보다_좁으면_붙이지_않는다() {
    // 사용자가 산 것보다 좁은 조건을 씌우게 된다. 실제로는 도난 외에도 보장되는데 화면에는
    // "도난만 보장"으로 나간다.
    givenRules(rule("휴대품손해(도난에 한함)", 0));
    CoverageItem item = item("휴대품손해");

    linker.link(analysis, List.of(item));

    assertThat(item.getTermsCoverage()).isNull();
  }

  @Test
  void 수식_후보가_둘_이상이면_붙이지_않는다() {
    // 어느 조항을 말하는지 가릴 수 없다. 아무거나 고르면 다른 담보의 면책이 이 담보의 것으로
    // 표시된다.
    givenRules(rule("상해의료비", 0), rule("질병의료비", 1));
    CoverageItem item = item("해외여행중 상해의료비 및 질병의료비");

    CoverageTermsLinkSummary summary = linker.link(analysis, List.of(item));

    assertThat(item.getTermsCoverage()).isNull();
    assertThat(summary.unlinked())
        .containsExactly(
            new UnlinkedCoverage("해외여행중 상해의료비 및 질병의료비", CoverageTermsUnlinkReason.AMBIGUOUS_TITLE));
  }

  @Test
  void 같은_이름의_규칙이_여럿이면_붙이지_않는다() {
    givenRules(rule("상해의료비", 0), rule("상해 의료비", 1));
    CoverageItem item = item("상해의료비");

    CoverageTermsLinkSummary summary = linker.link(analysis, List.of(item));

    assertThat(item.getTermsCoverage()).isNull();
    assertThat(summary.unlinked())
        .containsExactly(new UnlinkedCoverage("상해의료비", CoverageTermsUnlinkReason.AMBIGUOUS_TITLE));
  }

  @Test
  void 너무_짧은_규칙명은_품고_있어도_붙이지_않는다() {
    // "상해" 같은 두 글자 규칙 하나만 등록된 약관에서 무관한 담보에 걸리는 것을 막는다.
    givenRules(rule("상해", 0));
    CoverageItem item = item("해외여행중 상해의료비");

    linker.link(analysis, List.of(item));

    assertThat(item.getTermsCoverage()).isNull();
  }

  @Test
  void 맞는_규칙이_없으면_담보명을_요약에_남긴다() {
    // 이 이름이 약관 규칙 적재가 무엇을 빠뜨렸는지 알려주는 단서다.
    givenRules(rule("상해의료비", 0));
    CoverageItem item = item("항공기 지연 비용");

    CoverageTermsLinkSummary summary = linker.link(analysis, List.of(item));

    assertThat(item.getTermsCoverage()).isNull();
    assertThat(summary.unlinked())
        .containsExactly(
            new UnlinkedCoverage("항공기 지연 비용", CoverageTermsUnlinkReason.NOT_FOUND));
    assertThat(summary.linked()).isZero();
  }

  @Test
  void 약관이_연결되지_않았으면_이전_규칙_연결을_끊는다() {
    // 재분석으로 약관 연결이 달라졌는데 규칙만 남으면 다른 상품의 조항을 가리키게 된다.
    AnalysisResult unlinked = analysisLinkedTo(null);
    CoverageItem item = item("상해의료비");
    item.linkTermsCoverage(rule("상해의료비", 0));

    CoverageTermsLinkSummary summary = linker.link(unlinked, List.of(item));

    assertThat(item.getTermsCoverage()).isNull();
    assertThat(summary.total()).isEqualTo(1);
    assertThat(summary.linked()).isZero();
  }

  @Test
  void 약관에_규칙이_적재되지_않았으면_연결하지_않는다() {
    givenRules();
    CoverageItem item = item("상해의료비");

    CoverageTermsLinkSummary summary = linker.link(analysis, List.of(item));

    assertThat(item.getTermsCoverage()).isNull();
    assertThat(summary.unlinked())
        .containsExactly(new UnlinkedCoverage("상해의료비", CoverageTermsUnlinkReason.NOT_FOUND));
  }

  @Test
  void 여러_담보가_각자_맞는_규칙에_붙는다() {
    PolicyTermsCoverage medical = rule("상해의료비", 0);
    PolicyTermsCoverage baggage = rule("휴대품손해", 1);
    givenRules(medical, baggage);

    CoverageItem medicalItem = item("해외여행중 상해의료비(3천만원)");
    CoverageItem baggageItem = item("휴대품손해");
    CoverageItem unknown = item("항공기 지연");

    CoverageTermsLinkSummary summary =
        linker.link(analysis, List.of(medicalItem, baggageItem, unknown));

    assertThat(medicalItem.getTermsCoverage()).isSameAs(medical);
    assertThat(baggageItem.getTermsCoverage()).isSameAs(baggage);
    assertThat(unknown.getTermsCoverage()).isNull();
    assertThat(summary.total()).isEqualTo(3);
    assertThat(summary.exact()).isEqualTo(1);
    assertThat(summary.qualified()).isEqualTo(1);
    assertThat(summary.unlinked())
        .containsExactly(new UnlinkedCoverage("항공기 지연", CoverageTermsUnlinkReason.NOT_FOUND));
  }

  @Test
  void 이름이_겹치지_않아도_같은_분류_규칙이_하나면_붙인다() {
    // 증권 "해외의료실비보장"과 약관 "기본형 해외여행 실손의료비"는 같은 보장인데 글자가 하나도
    // 겹치지 않는다. 이름만으로는 영영 붙지 않는 케이스다.
    PolicyTermsCoverage rule = rule("기본형 해외여행 실손의료비", 0, "medical_expense");
    givenRules(rule);
    CoverageItem item = item("해외의료실비보장", "medical_expense");

    CoverageTermsLinkSummary summary = linker.link(analysis, List.of(item));

    assertThat(item.getTermsCoverage()).isSameAs(rule);
    assertThat(summary.category()).isEqualTo(1);
    assertThat(summary.linked()).isEqualTo(1);
  }

  @Test
  void 이름으로_붙었으면_분류는_보지_않는다() {
    // 신뢰도 순서다. 이름이 맞는데 분류가 다른 규칙을 가리킬 이유가 없다.
    PolicyTermsCoverage byTitle = rule("상해의료비", 0, "baggage");
    givenRules(byTitle, rule("휴대품손해", 1, "medical_expense"));
    CoverageItem item = item("상해의료비", "medical_expense");

    CoverageTermsLinkSummary summary = linker.link(analysis, List.of(item));

    assertThat(item.getTermsCoverage()).isSameAs(byTitle);
    assertThat(summary.exact()).isEqualTo(1);
    assertThat(summary.category()).isZero();
  }

  @Test
  void 같은_분류_규칙이_둘_이상이면_붙이지_않는다() {
    // category 는 여럿이 공유하라고 만든 값이라 이런 일이 정상적으로 생긴다. 하나를 고르면
    // 상해 담보에 질병 조항의 면책이 붙는다.
    givenRules(rule("상해의료비", 0, "medical_expense"), rule("질병의료비", 1, "medical_expense"));
    CoverageItem item = item("해외의료실비보장", "medical_expense");

    CoverageTermsLinkSummary summary = linker.link(analysis, List.of(item));

    assertThat(item.getTermsCoverage()).isNull();
    assertThat(summary.unlinked())
        .containsExactly(
            new UnlinkedCoverage("해외의료실비보장", CoverageTermsUnlinkReason.AMBIGUOUS_CATEGORY));
  }

  @Test
  void 이름이_모호했으면_분류로_내려가지_않는다() {
    // 이름으로 가리지 못한 것을 더 거친 분류로 가를 수는 없다. 그렇게 고른 하나는 동전 던지기다.
    givenRules(rule("상해의료비", 0, "baggage"), rule("상해 의료비", 1, "medical_expense"));
    CoverageItem item = item("상해의료비", "medical_expense");

    CoverageTermsLinkSummary summary = linker.link(analysis, List.of(item));

    assertThat(item.getTermsCoverage()).isNull();
    assertThat(summary.unlinked())
        .containsExactly(new UnlinkedCoverage("상해의료비", CoverageTermsUnlinkReason.AMBIGUOUS_TITLE));
  }

  @Test
  void 담보에_분류가_없으면_이름에서_끝난다() {
    // 약관 저장소가 생기기 전에 분석된 담보다. category 가 비어 있어도 앞 두 단계는 그대로 돈다.
    givenRules(rule("기본형 해외여행 실손의료비", 0, "medical_expense"));
    CoverageItem item = item("해외의료실비보장", null);

    CoverageTermsLinkSummary summary = linker.link(analysis, List.of(item));

    assertThat(item.getTermsCoverage()).isNull();
    assertThat(summary.unlinked())
        .containsExactly(new UnlinkedCoverage("해외의료실비보장", CoverageTermsUnlinkReason.NOT_FOUND));
  }

  @Test
  void 규칙에_분류가_없으면_후보로_보지_않는다() {
    givenRules(rule("기본형 해외여행 실손의료비", 0, null));
    CoverageItem item = item("해외의료실비보장", "medical_expense");

    linker.link(analysis, List.of(item));

    assertThat(item.getTermsCoverage()).isNull();
  }

  @Test
  void 표기가_달라도_같은_분류로_본다() {
    // 한쪽이 대문자나 공백을 섞어 보내도 같은 값이다.
    PolicyTermsCoverage rule = rule("기본형 해외여행 실손의료비", 0, " Medical_Expense ");
    givenRules(rule);
    CoverageItem item = item("해외의료실비보장", "medical_expense");

    linker.link(analysis, List.of(item));

    assertThat(item.getTermsCoverage()).isSameAs(rule);
  }

  @Test
  void 합의한_어휘_밖의_분류라도_양쪽이_같으면_붙인다() {
    // 어휘가 늘어난 날 연결이 통째로 끊기면 안 된다. 어긋난 사실은 로그로만 드러낸다.
    PolicyTermsCoverage rule = rule("반려동물 위탁비용", 0, "pet_boarding");
    givenRules(rule);
    CoverageItem item = item("반려동물보관비용", "pet_boarding");

    CoverageTermsLinkSummary summary = linker.link(analysis, List.of(item));

    assertThat(item.getTermsCoverage()).isSameAs(rule);
    assertThat(summary.category()).isEqualTo(1);
  }

  private void givenRules(PolicyTermsCoverage... rules) {
    given(policyTermsCoverageRepository.findByTermsIdOrderBySortOrderAsc(terms.getId()))
        .willReturn(List.of(rules));
  }

  private static PolicyTerms terms() {
    PolicyTerms terms = PolicyTerms.official("삼성화재", "해외여행보험", null, null);
    ReflectionTestUtils.setField(terms, "id", UUID.randomUUID());
    return terms;
  }

  private PolicyTermsCoverage rule(String title, int sortOrder) {
    return rule(title, sortOrder, null);
  }

  private PolicyTermsCoverage rule(String title, int sortOrder, String category) {
    PolicyTermsCoverage rule =
        PolicyTermsCoverage.builder()
            .terms(terms)
            .title(title)
            .category(category)
            .sortOrder(sortOrder)
            .build();
    ReflectionTestUtils.setField(rule, "id", UUID.randomUUID());
    return rule;
  }

  private AnalysisResult analysisLinkedTo(PolicyTerms matched) {
    PolicyDocument document =
        PolicyDocument.builder()
            .originalFilename("증권.pdf")
            .storedFilePath("/증권.pdf")
            .documentKind(DocumentKind.CERTIFICATE)
            .build();
    AnalysisResult result = AnalysisResult.builder().document(document).build();
    ReflectionTestUtils.setField(result, "id", UUID.randomUUID());
    result.completeWith(
        null, "{}", null, null, null, false, "삼성화재", "해외여행보험", null, null, LocalDateTime.now());
    result.linkTerms(matched);
    return result;
  }

  private CoverageItem item(String title) {
    return item(title, null);
  }

  private CoverageItem item(String title, String category) {
    CoverageItem item =
        CoverageItem.builder()
            .analysisResult(analysis)
            .title(title)
            .category(category)
            .coverageStatus(CoverageStatus.COVERED)
            .covered(true)
            .sortOrder(0)
            .build();
    ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
    return item;
  }
}
