package polight.server.domain.rag.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.analysis.entity.CoverageItem;
import polight.server.domain.analysis.entity.CoverageStatus;
import polight.server.domain.insurance.entity.DocumentKind;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.terms.entity.PolicyTerms;
import polight.server.domain.terms.entity.PolicyTermsChunk;

class CoverageItemSourceTest {

  @Test
  void 분석에_연결된_약관의_조항이면_근거로_붙는다() {
    PolicyTerms terms = terms("삼성화재", "해외여행보험");
    AnalysisResult analysis = analysisLinkedTo(terms);

    CoverageItemSource source =
        CoverageItemSource.builder()
            .coverageItem(coverageItem(analysis))
            .termsChunk(chunk(terms))
            .sourceRole(CoverageItemSourceRole.PRIMARY)
            .build();

    assertThat(source.getTermsChunk().getTerms()).isSameAs(terms);
  }

  @Test
  void 다른_약관의_조항은_근거로_붙일_수_없다() {
    // 이 검사가 없으면 화면에 그럴듯한 조항이 인용되지만 사용자가 가입한 상품과 무관한 문장이다.
    // 사용자는 그것을 보고 청구를 포기하거나, 되지 않을 청구를 준비한다.
    AnalysisResult analysis = analysisLinkedTo(terms("삼성화재", "해외여행보험"));
    PolicyTermsChunk otherChunk = chunk(terms("현대해상", "국내여행보험"));

    assertThatThrownBy(
            () ->
                CoverageItemSource.builder()
                    .coverageItem(coverageItem(analysis))
                    .termsChunk(otherChunk)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("연결된 약관의 조항");
  }

  @Test
  void 약관이_연결되지_않은_분석에는_근거를_달_수_없다() {
    // 어느 약관의 조항이 맞는지 확인할 기준 자체가 없다.
    AnalysisResult unlinked = analysisLinkedTo(null);

    assertThatThrownBy(
            () ->
                CoverageItemSource.builder()
                    .coverageItem(coverageItem(unlinked))
                    .termsChunk(chunk(terms("삼성화재", "해외여행보험")))
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("약관이 연결되지 않은");
  }

  private static PolicyTerms terms(String insurerName, String productName) {
    PolicyTerms terms = PolicyTerms.official(insurerName, productName, null, null);
    ReflectionTestUtils.setField(terms, "id", UUID.randomUUID());
    return terms;
  }

  private static AnalysisResult analysisLinkedTo(PolicyTerms terms) {
    PolicyDocument document =
        PolicyDocument.builder()
            .originalFilename("증권.pdf")
            .storedFilePath("/증권.pdf")
            .documentKind(DocumentKind.CERTIFICATE)
            .build();
    AnalysisResult analysis = AnalysisResult.builder().document(document).build();
    ReflectionTestUtils.setField(analysis, "id", UUID.randomUUID());
    analysis.linkTerms(terms);
    return analysis;
  }

  private static CoverageItem coverageItem(AnalysisResult analysis) {
    return CoverageItem.builder()
        .analysisResult(analysis)
        .title("상해의료비")
        .coverageStatus(CoverageStatus.COVERED)
        .covered(true)
        .sortOrder(0)
        .build();
  }

  private static PolicyTermsChunk chunk(PolicyTerms terms) {
    PolicyTermsChunk chunk =
        PolicyTermsChunk.builder()
            .terms(terms)
            .chunkIndex(0)
            .clausePath("제3관 제12조")
            .content("보험회사는 피보험자가 여행 중 상해로 의사의 치료를 받은 경우 ...")
            .build();
    ReflectionTestUtils.setField(chunk, "id", UUID.randomUUID());
    return chunk;
  }
}
