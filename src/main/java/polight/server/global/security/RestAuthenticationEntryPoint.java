package polight.server.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import polight.server.global.exception.ErrorCode;
import polight.server.global.exception.ErrorResponse;

/**
 * 인증되지 않은 요청에 대한 응답을 다른 예외와 같은 {@link ErrorResponse} 형식으로 맞춘다.
 *
 * <p>기본 설정에서는 토큰이 없는 요청이 익명 사용자로 처리되어 인가 단계에서 403이 나가고 본문도 비어 있다. 클라이언트가 "토큰 없음/만료"(401)와 "권한
 * 부족"(403)을 구분할 수 있도록 미인증은 401로 응답한다.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
      throws IOException {
    writeErrorResponse(response, objectMapper, ErrorCode.AUTHENTICATION_REQUIRED);
  }

  static void writeErrorResponse(
      HttpServletResponse response, ObjectMapper objectMapper, ErrorCode errorCode)
      throws IOException {
    response.setStatus(errorCode.getStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getWriter(), ErrorResponse.of(errorCode));
  }
}
