package polight.server.global.exception;

import lombok.Getter;

/**
 * 서비스에서 의도적으로 발생시키는 모든 예외의 단일 타입.
 *
 * <p>도메인마다 예외 클래스를 상속해 늘리지 않고, 어떤 상황인지는 {@link ErrorCode}로 구분한다. 상태 코드와 기본 메시지는 ErrorCode가 들고 있으므로
 * 호출부는 어떤 상황인지만 지정하면 된다.
 */
@Getter
public class BaseException extends RuntimeException {

  private final ErrorCode errorCode;

  public BaseException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  /** 원인 예외를 함께 남겨야 할 때 사용한다. 로그에는 원인이 남고 응답에는 노출되지 않는다. */
  public BaseException(ErrorCode errorCode, Throwable cause) {
    super(errorCode.getMessage(), cause);
    this.errorCode = errorCode;
  }
}
