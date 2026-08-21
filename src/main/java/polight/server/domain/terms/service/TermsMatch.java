package polight.server.domain.terms.service;

import polight.server.domain.terms.entity.PolicyTerms;

/**
 * 약관 매칭 결과.
 *
 * @param stage 어느 단계에서 찾았는지. {@link TermsMatchStage#NONE}이면 {@code terms}는 {@code null}이다
 * @param terms 찾아낸 약관. 못 찾았으면 {@code null}
 * @param reason 사람이 읽을 수 있는 판단 근거. 로그와 운영 문의 대응에 쓴다
 */
public record TermsMatch(TermsMatchStage stage, PolicyTerms terms, String reason) {

  public static TermsMatch found(TermsMatchStage stage, PolicyTerms terms, String reason) {
    return new TermsMatch(stage, terms, reason);
  }

  public static TermsMatch none(String reason) {
    return new TermsMatch(TermsMatchStage.NONE, null, reason);
  }

  public boolean isMatched() {
    return stage != TermsMatchStage.NONE && terms != null;
  }
}
