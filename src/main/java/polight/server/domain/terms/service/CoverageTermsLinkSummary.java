package polight.server.domain.terms.service;

import java.util.List;

/**
 * 담보를 약관 규칙에 붙인 결과 요약.
 *
 * <p>담보는 한 분석에 수십 건이라 건별로 로그를 남기면 읽을 수 없다. 대신 몇 건이 어떤 단계로 붙었는지와, 붙지 않은 담보명을 모아 남긴다. 붙지 않은
 * 이름은 약관 규칙 적재가 무엇을 빠뜨렸는지 알려주는 유일한 단서다.
 *
 * @param total 대상 담보 수
 * @param exact 표기까지 같아 붙은 수
 * @param qualified 증권이 수식어를 덧붙인 형태로 붙은 수
 * @param unlinkedTitles 붙지 않은 담보명
 */
public record CoverageTermsLinkSummary(
    int total, int exact, int qualified, List<String> unlinkedTitles) {

  public static CoverageTermsLinkSummary empty() {
    return new CoverageTermsLinkSummary(0, 0, 0, List.of());
  }

  public int linked() {
    return exact + qualified;
  }
}
