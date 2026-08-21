package polight.server.domain.terms.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PolicyTermsCoverageSourceTest {

  @Test
  void 같은_약관의_조항이면_근거로_붙는다() {
    PolicyTerms terms = terms("삼성화재", "해외여행보험");

    PolicyTermsCoverageSource source =
        PolicyTermsCoverageSource.builder()
            .termsCoverage(coverage(terms, "상해의료비"))
            .termsChunk(chunk(terms))
            .sourceRole(TermsCoverageSourceRole.EXCLUSION)
            .quoteText("전문등반 중 발생한 상해는 보상하지 않습니다.")
            .build();

    assertThat(source.getTermsChunk().getTerms()).isSameAs(terms);
    assertThat(source.getSourceRole()).isEqualTo(TermsCoverageSourceRole.EXCLUSION);
  }

  @Test
  void 다른_약관의_조항은_근거로_붙일_수_없다() {
    // 붙으면 화면에 그럴듯한 조항이 인용되지만 그 상품과 무관한 문장이다. 사용자는 그것을 보고
    // 청구를 포기하거나, 되지 않을 청구를 준비한다.
    PolicyTermsCoverage coverage = coverage(terms("삼성화재", "해외여행보험"), "상해의료비");
    PolicyTermsChunk otherChunk = chunk(terms("현대해상", "국내여행보험"));

    assertThatThrownBy(
            () ->
                PolicyTermsCoverageSource.builder()
                    .termsCoverage(coverage)
                    .termsChunk(otherChunk)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("같은 약관의 조항");
  }

  @Test
  void 역할을_지정하지_않으면_PRIMARY_다() {
    PolicyTerms terms = terms("삼성화재", "해외여행보험");

    PolicyTermsCoverageSource source =
        PolicyTermsCoverageSource.builder()
            .termsCoverage(coverage(terms, "상해의료비"))
            .termsChunk(chunk(terms))
            .build();

    assertThat(source.getSourceRole()).isEqualTo(TermsCoverageSourceRole.PRIMARY);
  }

  private static PolicyTerms terms(String insurerName, String productName) {
    PolicyTerms terms = PolicyTerms.official(insurerName, productName, null, null);
    ReflectionTestUtils.setField(terms, "id", UUID.randomUUID());
    return terms;
  }

  private static PolicyTermsCoverage coverage(PolicyTerms terms, String title) {
    PolicyTermsCoverage coverage =
        PolicyTermsCoverage.builder().terms(terms).title(title).sortOrder(0).build();
    ReflectionTestUtils.setField(coverage, "id", UUID.randomUUID());
    return coverage;
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
