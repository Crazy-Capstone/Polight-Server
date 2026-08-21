package polight.server.domain.terms.service;

import java.util.List;
import java.util.UUID;

/**
 * 백필 한 번의 결과.
 *
 * <p>운영에서 이 값만 보고 "돌릴 만했는지"를 판단할 수 있어야 한다. 그래서 처리한 수뿐 아니라 <b>붙지 않은 수</b>를 함께 담는다. 붙지 않은
 * 것이 대부분이면 약관 적재가 덜 된 것이지 백필이 실패한 것이 아니다.
 *
 * @param processed 대상이 된 분석 수
 * @param termsMatched 약관이 붙은 분석 수
 * @param termsUnmatched 약관을 찾지 못한 분석 수. 그 상품 약관이 아직 없거나 증권에서 보험사명을 읽지 못한 건들이다
 * @param coveragesLinked 규칙이 붙은 담보 수
 * @param coveragesTotal 대상이 된 담보 수
 * @param failedAnalysisIds 처리 중 예외가 난 분석. 이 건들만 남은 것이므로 원인을 고친 뒤 다시 돌리면 된다
 */
public record TermsBackfillSummary(
    int processed,
    int termsMatched,
    int termsUnmatched,
    int coveragesLinked,
    int coveragesTotal,
    List<UUID> failedAnalysisIds) {

  public static TermsBackfillSummary empty() {
    return new TermsBackfillSummary(0, 0, 0, 0, 0, List.of());
  }

  public int failed() {
    return failedAnalysisIds.size();
  }
}
