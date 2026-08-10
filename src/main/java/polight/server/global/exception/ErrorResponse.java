package polight.server.global.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * 모든 에러 응답의 공통 형식.
 *
 * @param code {@link ErrorCode} 이름. 클라이언트는 메시지가 아닌 이 값으로 분기한다.
 * @param message 사용자에게 보여줄 수 있는 한국어 설명
 * @param fieldErrors 요청 본문 검증에 실패한 필드별 사유. 해당 없으면 응답에서 생략된다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, Map<String, String> fieldErrors) {

  public static ErrorResponse of(ErrorCode errorCode) {
    return new ErrorResponse(errorCode.name(), errorCode.getMessage(), null);
  }

  public static ErrorResponse of(ErrorCode errorCode, Map<String, String> fieldErrors) {
    return new ErrorResponse(errorCode.name(), errorCode.getMessage(), fieldErrors);
  }
}
