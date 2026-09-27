package polight.server.domain.terms.service;

import polight.server.domain.terms.entity.PolicyTerms;

/**
 * 약관 연결이 확실하지 않을 때 사용자에게 보여줄 문구를 만든다.
 *
 * <p>{@code TermsMatch.reason}과 다르다. 그쪽은 개발자가 로그에서 읽는 문장이라 {@code "상품명('해외여행보험 플러스')은 맞지
 * 않지만 '삼성화재' 약관이 '해외여행보험' 하나뿐입니다"} 처럼 적혀 있다. 그대로 화면에 띄우면 사용자는 무슨 말인지 알 수 없다.
 *
 * <p>확실한 경우({@link TermsMatchStage#EXACT})에는 {@code null}이다. 맞는 약관을 찾았다는 사실까지 알릴 필요는 없고,
 * 매번 무언가 뜨면 정작 경고가 필요할 때 눈에 띄지 않는다.
 *
 * <p>{@code AiTermsMatcher}는 이 클래스를 쓰지 않는다. AI 서버가 자기 판단에 맞는 문구를 함께 보내고, 그쪽이 어떤 근거로
 * 골랐는지 더 잘 안다.
 */
final class TermsMatchNotice {

  private TermsMatchNotice() {}

  /** 약관을 아예 찾지 못했을 때. 왜 못 찾았는지는 사용자에게 의미가 없어 한 문장으로 고정한다. */
  static final String NOT_FOUND =
      "가입하신 상품의 약관을 찾지 못해, 증권에 적힌 내용만 안내해 드려요.";

  static String of(TermsMatchStage stage, PolicyTerms terms) {
    return switch (stage) {
      case EXACT -> null;
      case REVISION -> revision(terms);
      case INSURER -> insurer(terms);
      case NONE -> NOT_FOUND;
    };
  }

  /**
   * 개정판이 여럿이라 가입 시점으로 하나를 고른 경우.
   *
   * <p>고른 판의 날짜를 밝힌다. 사용자가 자기 증권의 약관 날짜와 대조할 수 있는 유일한 단서다.
   */
  private static String revision(PolicyTerms terms) {
    if (terms == null || terms.getEffectiveDate() == null) {
      return "약관 개정판이 여러 건이라 가입 시점에 가까운 것으로 안내해 드려요. 실제 가입하신 약관과 다를 수 있어요.";
    }
    return "%s 개정 약관을 기준으로 안내해 드려요. 실제 가입하신 약관과 다를 수 있어요."
        .formatted(terms.getEffectiveDate());
  }

  /**
   * 상품명이 맞지 않아 보험사 약관 하나로 대신한 경우.
   *
   * <p>가장 약한 연결이라 "찾지 못했다"는 사실을 먼저 말한다. 어느 약관을 대신 썼는지 밝혀야 사용자가 자기 상품과 다르다는 것을
   * 알아차릴 수 있다.
   */
  private static String insurer(PolicyTerms terms) {
    if (terms == null) {
      return NOT_FOUND;
    }
    return "가입하신 상품의 약관을 정확히 찾지 못해, %s의 %s 약관을 기준으로 안내해 드려요."
        .formatted(terms.getInsurerName(), terms.getProductName());
  }
}
