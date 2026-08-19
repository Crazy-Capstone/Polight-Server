package polight.server.global.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** 모든 예외를 {@link ErrorResponse} 하나의 형식으로 변환한다. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  /** 서비스가 의도적으로 던진 예외. 상태 코드는 {@link ErrorCode}가 정한다. */
  @ExceptionHandler(BaseException.class)
  public ResponseEntity<ErrorResponse> handleBaseException(BaseException e) {
    ErrorCode errorCode = e.getErrorCode();

    // 5xx는 서버 문제이므로 원인까지 남긴다. 4xx는 정상적인 클라이언트 오류라 로그를 남기지 않는다.
    if (errorCode.getStatus().is5xxServerError()) {
      log.error("[{}] {}", errorCode.name(), errorCode.getMessage(), e);
    }

    return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode));
  }

  /** 요청 본문 검증(@Valid) 실패. 어느 필드가 왜 틀렸는지 함께 내려준다. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

    return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
        .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, fieldErrors));
  }

  /**
   * 본문이 JSON으로 파싱되지 않는 경우.
   *
   * <p>이 핸들러가 없으면 맨 아래 {@code Exception} 핸들러가 받아 500이 된다. 하지만 읽을 수 없는 본문은 보낸 쪽의 문제이므로 400이 맞다.
   * 특히 AI 서버 콜백은 실패 시 3회 재시도하므로, 5xx로 돌려주면 절대 성공할 수 없는 요청을 세 번 더 받게 된다.
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
      HttpMessageNotReadableException e) {
    log.warn("요청 본문을 읽을 수 없습니다: {}", e.getMostSpecificCause().getMessage());

    return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
        .body(ErrorResponse.of(ErrorCode.INVALID_INPUT));
  }

  /** 경로 변수·쿼리 파라미터 타입 불일치(예: UUID 자리에 잘못된 문자열). */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
    Map<String, String> fieldErrors = Map.of(e.getName(), "형식이 올바르지 않습니다.");

    return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
        .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, fieldErrors));
  }

  /** multipart 업로드 크기 초과. 서블릿 단계에서 걸리므로 서비스까지 오지 않는다. */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
    return ResponseEntity.status(ErrorCode.POLICY_DOCUMENT_TOO_LARGE.getStatus())
        .body(ErrorResponse.of(ErrorCode.POLICY_DOCUMENT_TOO_LARGE));
  }

  /**
   * 예상하지 못한 예외.
   *
   * <p>이 핸들러가 없으면 처리되지 않은 예외가 /error로 forward되면서 상태 코드가 인증 실패(401/403)로 덮여, 서버 오류를 인증 문제로 오인하게 된다.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleException(Exception e) {
    log.error("처리되지 않은 예외", e);

    return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
        .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
  }
}
