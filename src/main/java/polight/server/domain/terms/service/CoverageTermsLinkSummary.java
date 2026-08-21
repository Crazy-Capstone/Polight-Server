package polight.server.domain.terms.service;

import java.util.List;

/**
 * 담보를 약관 규칙에 붙인 결과 요약.
 *
 * <p>담보는 한 분석에 수십 건이라 건별로 로그를 남기면 읽을 수 없다. 대신 몇 건이 어떤 단계로 붙었는지와, 붙지 않은 담보를 사유와 함께 모아 남긴다.
 *
 * <p><b>단계를 나눠 세는 것이 중요하다.</b> 합쳐 세면 category로만 붙은 비율이 보이지 않는데, 그 비율이 높다는 것은 약관 규칙의 {@code
 * title} 적재가 부실하다는 신호다. 연결 건수만 보면 잘 돌아가는 것처럼 보인다.
 *
 * @param total 대상 담보 수
 * @param exact 표기까지 같아 붙은 수
 * @param qualified 증권이 수식어를 덧붙인 형태로 붙은 수
 * @param category 이름으로는 못 붙고 표준 분류로 붙은 수
 * @param unlinked 붙지 않은 담보와 그 사유
 */
public record CoverageTermsLinkSummary(
    int total, int exact, int qualified, int category, List<UnlinkedCoverage> unlinked) {

  /** @param reason 고쳐야 할 사람이 사유마다 다르다. {@link CoverageTermsUnlinkReason} 참고 */
  public record UnlinkedCoverage(String title, CoverageTermsUnlinkReason reason) {}

  public static CoverageTermsLinkSummary empty() {
    return new CoverageTermsLinkSummary(0, 0, 0, 0, List.of());
  }

  public int linked() {
    return exact + qualified + category;
  }
}
