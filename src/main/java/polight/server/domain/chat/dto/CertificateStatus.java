package polight.server.domain.chat.dto;

/** AI 요청에 전달하는 증권 상태. 미등록은 분석 행으로 저장하지 않는다. */
public enum CertificateStatus {
  UNREGISTERED,
  PROCESSING,
  FAILED,
  COMPLETED
}
