package polight.server.domain.terms.service;

import polight.server.domain.analysis.entity.AnalysisResult;

/**
 * "이 증권은 어느 약관인가"를 판단한다.
 *
 * <p>구현이 둘이다. {@link LocalTermsMatcher}는 백엔드가 {@code policy_terms}를 직접 뒤지고, {@code
 * AiTermsMatcher}는 AI 서버에 물어본다. {@code terms.matching.provider} 설정으로 고른다.
 *
 * <p>둘로 나눈 이유는 AI 쪽이 우리가 갖지 못한 지식을 쓰기 때문이다 -- 약관코드, 보험사 별칭, 인수사 이력(한화손해보험이 인수하기 전 캐롯 약관 같은
 * 것). 표기를 다듬는 것만으로는 닿지 않는 값이라 코드로 재현할 수 없다.
 *
 * <p>그래도 전환은 설정 한 줄로 되돌릴 수 있어야 한다. 잘못 연결된 약관은 연결이 없는 것보다 나쁘고, 그 판정을 통째로 외부에 맡기는 일이라
 * 되돌릴 길을 열어 둔다.
 */
public interface TermsMatcher {

  /**
   * 연결하지 않고 판단만 한다.
   *
   * <p>찾지 못하면 {@link TermsMatch#none}이다. <b>예외가 아니라 정상 갈래다</b> -- 그 상품 약관이 아직 적재되지 않았거나, 후보가
   * 여럿이라 가릴 수 없었던 경우다.
   */
  TermsMatch match(AnalysisResult analysisResult);
}
