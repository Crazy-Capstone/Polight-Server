package polight.server.domain.auth.exception;

import lombok.Getter;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@Getter
public class SignupRequiredException extends BaseException {

  private final String signupToken;
  private final long expiresInSeconds;

  public SignupRequiredException(String signupToken, long expiresInSeconds) {
    super(ErrorCode.SIGNUP_REQUIRED);
    this.signupToken = signupToken;
    this.expiresInSeconds = expiresInSeconds;
  }
}
