package polight.server.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
  FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청값이 올바르지 않습니다."),
  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
  TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "여행 정보를 찾을 수 없습니다."),
  POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "보험 정보를 찾을 수 없습니다."),
  COVERAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "보장 항목을 찾을 수 없습니다."),
  DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "보험 문서를 찾을 수 없습니다."),
  NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),
  UNSUPPORTED_FILE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "PDF 파일만 업로드할 수 있습니다."),
  FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "업로드 가능한 파일 크기를 초과했습니다."),
  ANALYSIS_STATE_CONFLICT(HttpStatus.CONFLICT, "현재 문서 상태에서는 분석을 요청할 수 없습니다."),
  FILE_STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장 중 오류가 발생했습니다."),
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

  private final HttpStatus status;
  private final String message;

  ErrorCode(HttpStatus status, String message) {
    this.status = status;
    this.message = message;
  }

  public HttpStatus status() { return status; }
  public String message() { return message; }
}
