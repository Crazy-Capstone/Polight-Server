package polight.server.domain.terms.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InsuranceNameNormalizerTest {

  @Test
  void 법인격_표기가_달라도_같은_보험사로_본다() {
    // 증권 OCR 결과와 운영자 입력값이 이렇게 갈린다. 여기서 못 맞추면 약관이 등록되어 있는데도
    // "약관을 찾지 못했습니다"가 뜬다.
    assertThat(InsuranceNameNormalizer.matches("삼성화재해상보험(주)", "삼성화재해상보험 주식회사")).isTrue();
    assertThat(InsuranceNameNormalizer.matches("삼성화재해상보험㈜", "삼성화재해상보험")).isTrue();
  }

  @Test
  void 공백과_구두점_차이를_무시한다() {
    assertThat(InsuranceNameNormalizer.matches("무배당 다이렉트 해외여행보험", "무배당다이렉트해외여행보험")).isTrue();
    assertThat(InsuranceNameNormalizer.matches("해외여행보험 - 실속형", "해외여행보험실속형")).isTrue();
  }

  @Test
  void 영문_대소문자를_무시한다() {
    assertThat(InsuranceNameNormalizer.matches("Direct 해외여행", "direct해외여행")).isTrue();
  }

  @Test
  void 이름이_실제로_다르면_맞추지_않는다() {
    // 표기만 다듬을 뿐 유사도 비교는 하지 않는다. 여기서 붙어 버리면 다른 상품의 약관을 근거로
    // 답하게 된다.
    assertThat(InsuranceNameNormalizer.matches("해외여행보험", "해외여행보험(실속형)")).isFalse();
    assertThat(InsuranceNameNormalizer.matches("삼성화재", "현대해상")).isFalse();
  }

  @Test
  void 비어_있는_값은_서로_같다고_보지_않는다() {
    // null 끼리 같다고 하면, 보험사명을 못 읽은 증권이 보험사명 없는 약관에 붙는다.
    assertThat(InsuranceNameNormalizer.matches(null, null)).isFalse();
    assertThat(InsuranceNameNormalizer.matches("", "")).isFalse();
    assertThat(InsuranceNameNormalizer.matches("(주)", "  ")).isFalse();
  }

  @Test
  void 다듬고_나서_남는_것이_없으면_null_이다() {
    assertThat(InsuranceNameNormalizer.normalize(null)).isNull();
    assertThat(InsuranceNameNormalizer.normalize("   ")).isNull();
    // 법인격 표기만 있는 값은 이름이 아니다.
    assertThat(InsuranceNameNormalizer.normalize("(주)")).isNull();
    assertThat(InsuranceNameNormalizer.normalize("---")).isNull();
  }
}
