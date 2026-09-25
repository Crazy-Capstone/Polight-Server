package polight.server.domain.terms.dto;

import java.util.UUID;

/**
 * AI 서버의 약관 매칭 결과.
 *
 * <p>못 찾았을 때도 {@code 200}으로 오고 {@code termsId}만 비어 있다. 찾지 못하는 것은 오류가 아니라 정상 갈래라서다 -- 아직 적재되지
 * 않은 상품이거나, 후보가 여럿이라 가릴 수 없었던 경우다.
 *
 * @param termsId 고른 약관. 못 찾았으면 {@code null}
 * @param level 어느 단계로 찾았는지. {@code EXACT} 등. 어휘가 늘 수 있어 문자열로 받는다
 * @param notice 사용자에게 보여줄 안내. 구판으로 답하는 경우처럼 확실하지 않을 때만 채워지고, 깔끔하게 맞았으면 {@code null}이다
 * @param insurerName AI 가 고른 약관의 보험사명. 증권에서 읽은 이름과 다를 수 있다(별칭·인수사 이력)
 * @param revision 고른 개정판의 날짜
 */
public record TermsMatchResponse(
    UUID termsId,
    String level,
    String notice,
    String insurerName,
    String productName,
    String revision) {}
