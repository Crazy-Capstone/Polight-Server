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

    linker.link(analysis, List.of(item));

    assertThat(item.getTermsCoverage()).isNull();
  }

  @Test
  void 같은_이름의_규칙이_여럿이면_붙이지_않는다() {
    givenRules(rule("상해의료비", 0), rule("상해 의료비", 1));
    CoverageItem item = item("상해의료비");

    linker.link(analysis, List.of(item));

    assertThat(item.getTermsCoverage()).isNull();
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
    assertThat(summary.unlinkedTitles()).containsExactly("항공기 지연 비용");
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
    assertThat(summary.unlinkedTitles()).containsExactly("상해의료비");
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
    assertThat(summary.unlinkedTitles()).containsExactly("항공기 지연");
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
    PolicyTermsCoverage rule =
        PolicyTermsCoverage.builder().terms(terms).title(title).sortOrder(sortOrder).build();
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
    result.completeWith(null, "{}", null, null, null, false, "삼성화재", "해외여행보험", LocalDateTime.now());
    result.linkTerms(matched);
    return result;
  }

  private CoverageItem item(String title) {
    CoverageItem item =
        CoverageItem.builder()
            .analysisResult(analysis)
            .title(title)
            .coverageStatus(CoverageStatus.COVERED)
            .covered(true)
            .sortOrder(0)
            .build();
    ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
    return item;
  }
}
