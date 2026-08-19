package polight.server.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import polight.server.global.exception.ErrorCode;

/**
 * AI 서버와 주고받는 {@code /internal/**} 요청을 공유 시크릿으로 인증한다.
 *
 * <p>이 경로는 사용자 토큰이 없다. AI 서버가 분석 콜백을 보낼 때 쓰는 서버 대 서버 구간이라 JWT 대신 양쪽이 같은 값을 갖는 키 하나로 인증한다. 같은 키를
 * 백엔드가 AI를 호출할 때도 헤더에 실어 보낸다.
 *
 * <p>비교는 {@link MessageDigest#isEqual}로 한다. {@code String.equals}는 첫 불일치 문자에서 즉시 반환하므로, 응답 시간 차이로 키를
 * 앞에서부터 한 글자씩 알아낼 수 있다.
 *
 * <p>키가 설정되지 않은 환경에서는 {@code /internal/**}을 전부 거부한다(fail closed). 로컬 개발에서 키 없이 서버를 띄우는 것은 그대로 되고,
 * 대신 내부 경로만 쓸 수 없다. 키가 없을 때 통과시키면 배포 환경에서 환경변수 하나가 빠졌을 때 인증이 조용히 사라진다.
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

  public static final String HEADER_NAME = "X-Internal-Api-Key";

  private static final String INTERNAL_PATH_PREFIX = "/internal/";

  private final ObjectMapper objectMapper;
  private final byte[] apiKey;

  public InternalApiKeyFilter(
      ObjectMapper objectMapper, @Value("${internal.api-key:}") String apiKey) {
    this.objectMapper = objectMapper;
    this.apiKey = toBytes(apiKey);

    if (this.apiKey.length == 0) {
      logger.warn(
          "internal.api-key 가 설정되지 않았습니다. /internal/** 요청을 모두 거부합니다. "
              + "AI 서버 연동이 필요하면 INTERNAL_API_KEY 환경변수를 주입하세요.");
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!isAuthorized(request.getHeader(HEADER_NAME))) {
      // 키 값 자체는 로그에 남기지 않는다. 경로만으로 어느 호출이 막혔는지 알 수 있다.
      logger.warn("내부 API 키 인증 실패: " + request.getMethod() + " " + request.getRequestURI());
      RestAuthenticationEntryPoint.writeErrorResponse(
          response, objectMapper, ErrorCode.AUTHENTICATION_REQUIRED);
      return;
    }

    filterChain.doFilter(request, response);
  }

  private boolean isAuthorized(String providedKey) {
    if (apiKey.length == 0) {
      return false;
    }
    return MessageDigest.isEqual(apiKey, toBytes(providedKey));
  }

  /** 헤더가 없으면 빈 배열이 된다. {@code MessageDigest.isEqual}은 길이가 달라도 안전하게 false를 돌려준다. */
  private static byte[] toBytes(String value) {
    if (value == null) {
      return new byte[0];
    }
    return value.strip().getBytes(StandardCharsets.UTF_8);
  }
}
