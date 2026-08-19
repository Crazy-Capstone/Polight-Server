package polight.server.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 서비스에서 발생하는 모든 예외 상황을 한 곳에서 정의한다.
 *
 * <p>도메인별로 예외 클래스를 따로 두지 않고 {@link BaseException} 하나에 이 enum을 담아 구분한다. 응답의 code 필드로는 enum 이름을 그대로
 * 내보내므로, 클라이언트는 한국어 메시지를 파싱하지 않고 code로 분기할 수 있다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

  // 공통
  INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

  // 인증 / 인가
  AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
  ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
  KAKAO_TOKEN_REQUEST_FAILED(HttpStatus.UNAUTHORIZED, "카카오 토큰 발급에 실패했습니다."),
  KAKAO_USER_INFO_REQUEST_FAILED(HttpStatus.UNAUTHORIZED, "카카오 사용자 정보 조회에 실패했습니다."),
  KAKAO_USER_ID_NOT_FOUND(HttpStatus.UNAUTHORIZED, "카카오 사용자 식별자(providerId)가 없습니다."),

  // 사용자
  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

  // 여행
  TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다."),
  INVALID_TRIP_PERIOD(HttpStatus.BAD_REQUEST, "여행 종료일은 시작일보다 빠를 수 없습니다."),

  // 약관 문서
  POLICY_DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "약관 문서를 찾을 수 없습니다."),
  EMPTY_POLICY_DOCUMENT_FILE(HttpStatus.BAD_REQUEST, "업로드할 약관 파일은 비어 있을 수 없습니다."),
  POLICY_DOCUMENT_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "업로드할 수 있는 파일 크기를 초과했습니다."),
  POLICY_DOCUMENT_STORAGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "약관 파일을 저장하지 못했습니다."),
  POLICY_DOCUMENT_URL_GENERATION_FAILED(
      HttpStatus.INTERNAL_SERVER_ERROR, "약관 파일 다운로드 URL을 생성하지 못했습니다."),

  // 분석
  ANALYSIS_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, "분석 결과를 찾을 수 없습니다."),
  AI_ANALYSIS_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "AI 서버에 분석을 요청하지 못했습니다.");

  private final HttpStatus status;
  private final String message;
}
