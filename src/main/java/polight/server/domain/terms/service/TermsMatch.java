package polight.server.domain.terms.service;

import polight.server.domain.terms.entity.PolicyTerms;

/**
 * 약관 매칭 결과.
 *
 * @param stage 어느 단계에서 찾았는지. {@link TermsMatchStage#NONE}이면 {@code terms}는 {@code null}이다
 * @param terms 찾아낸 약관. 못 찾았으면 {@code null}
 * @param reason 사람이 읽을 수 있는 판단 근거. <b>개발자용</b>이다 -- 로그와 운영 문의 대응에 쓴다
 * @param notice 사용자에게 보여줄 안내. 확실하게 찾았으면 {@code null}이라 화면에 아무것도 뜨지 않는다.
 *     {@code reason}과 섞으면 안 된다 -- 한쪽은 원인을 캐는 사람이, 다른 쪽은 보험금을 청구할지 정하는 사람이 읽는다
 */
public record TermsMatch(
    TermsMatchStage stage, PolicyTerms terms, String reason, String notice) {

  public static TermsMatch found(
      TermsMatchStage stage, PolicyTerms terms, String reason, String notice) {
    return new TermsMatch(stage, terms, reason, notice);
  }

  /** 찾지 못했다. 안내 문구는 정해진 한 문장이다 -- 왜 못 찾았는지는 사용자에게 의미가 없다. */
  public static TermsMatch none(String reason) {
    return none(reason, null);
  }

  /**
   * 찾지 못했는데 알릴 문구가 따로 있는 경우. AI 서버가 보낸 안내를 그대로 흘릴 때 쓴다.
   *
   * @param notice {@code null}이면 정해진 문구로 대신한다
   */
  public static TermsMatch none(String reason, String notice) {
    return new TermsMatch(
        TermsMatchStage.NONE, null, reason, notice == null ? TermsMatchNotice.NOT_FOUND : notice);
  }

  public boolean isMatched() {
    return stage != TermsMatchStage.NONE && terms != null;
  }
}
