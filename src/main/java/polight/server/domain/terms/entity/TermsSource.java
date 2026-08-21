package polight.server.domain.terms.entity;

/**
 * 약관이 어디서 왔는지.
 *
 * <p>{@link TermsVerificationStatus}와 별개로 둔다. "사용자가 올렸지만 운영자가 확인해 공용이 된 약관"이 있을 수 있고, 그때 출처를 잃지
 * 않아야 한다.
 */
public enum TermsSource {
  /** 운영자가 보험사 공시자료에서 받아 등록한 것. */
  OFFICIAL,
  /** 사용자가 올린 것. */
  USER_UPLOAD
}
