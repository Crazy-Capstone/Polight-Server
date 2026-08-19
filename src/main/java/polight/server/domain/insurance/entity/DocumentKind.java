package polight.server.domain.insurance.entity;

/** 업로드된 문서의 종류. 증권은 사용자별 가입 정보, 약관은 상품 공용 문서다. */
public enum DocumentKind {
  /** 보험증권. 가입 담보와 가입금액이 들어 있다. */
  CERTIFICATE,
  /** 보험 약관. 보장 조항과 면책 사항이 들어 있다. */
  TERMS
}
