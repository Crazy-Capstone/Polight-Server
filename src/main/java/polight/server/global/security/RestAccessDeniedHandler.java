package polight.server.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import polight.server.global.exception.ErrorCode;
import polight.server.global.exception.ErrorResponse;

/** 인증은 되었으나 권한이 없는 요청의 응답 형식을 통일한다. */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
      throws IOException {
    RestAuthenticationEntryPoint.writeErrorResponse(response, objectMapper, ErrorCode.ACCESS_DENIED);
  }
}
