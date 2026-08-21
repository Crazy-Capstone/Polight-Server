package polight.server.domain.terms.entity;

/**
 * 약관을 누구에게까지 보여줄 수 있는지를 가르는 값.
 *
 * <p>사용자 업로드를 곧바로 공용으로 쓰면 잘못된 파일 하나가 같은 상품 가입자 전원의 답변 근거를 오염시킨다. 확인 전에는 올린 사람 밖으로 나가지 않게 막는다.
 */
public enum TermsVerificationStatus {
  /** 아직 확인되지 않은 약관. 올린 사용자에게만 보인다. */
  UNVERIFIED,
  /** 운영자가 확인한 공용 약관. 같은 상품 가입자 모두가 공유한다. */
  VERIFIED
}
