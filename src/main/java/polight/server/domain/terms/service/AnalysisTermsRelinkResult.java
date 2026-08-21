package polight.server.domain.terms.service;

import java.util.UUID;

/**
 * 분석 한 건을 다시 연결한 결과.
 *
 * @param termsMatched 약관이 붙었는지. 백필을 돌려도 붙지 않는 건이 남는 것이 정상이다 -- 그 상품의 약관이 아직 적재되지 않았거나, 증권에서
 *     보험사명을 읽지 못한 분석이다
 * @param coverages 담보를 약관 규칙에 붙인 결과
 */
public record AnalysisTermsRelinkResult(
    UUID analysisResultId, boolean termsMatched, CoverageTermsLinkSummary coverages) {}
