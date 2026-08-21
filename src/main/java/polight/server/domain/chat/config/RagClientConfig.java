package polight.server.domain.chat.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 챗봇 질의용 RestClient.
 *
 * <p>{@code aiRestClient}를 재사용하지 않는 이유는 두 가지다.
 *
 * <ul>
 *   <li>read-timeout이 다르다. 분석 요청은 AI가 202만 돌려주고 끊어 10s로 충분하지만, 질의는 검색과 LLM 생성이 끝날 때까지 기다린다. 같은
 *       빈을 쓰면 정상 응답을 타임아웃으로 오인한다
 *   <li>{@code aiRestClient}는 {@code storage.type=s3} 조건이 붙어 있다. 분석 요청은 S3 presigned URL이 있어야 성립하니
 *       맞는 조건이지만, 질의는 S3와 무관하다. 같은 조건을 달면 로컬 개발에서 빈이 없어 컨텍스트가 뜨지 않는다
 * </ul>
 */
@Configuration
public class RagClientConfig {

  @Bean("ragRestClient")
  public RestClient ragRestClient(
      RestClient.Builder builder,
      @Value("${ai.server.base-url}") String baseUrl,
      @Value("${ai.server.connect-timeout}") Duration connectTimeout,
      @Value("${ai.server.rag-read-timeout}") Duration readTimeout) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeout);
    requestFactory.setReadTimeout(readTimeout);
    return builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
  }
}
