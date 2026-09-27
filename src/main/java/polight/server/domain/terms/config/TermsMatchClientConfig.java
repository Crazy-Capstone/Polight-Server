package polight.server.domain.terms.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 약관 매칭 질의용 RestClient.
 *
 * <p>{@code aiRestClient}·{@code ragRestClient}를 재사용하지 않는다. 앞의 것은 {@code storage.type=s3} 조건이
 * 붙어 있어 매칭과 무관한 이유로 빈이 사라지고, 뒤의 것은 LLM 생성을 기다리느라 읽기 타임아웃이 60초다. 매칭은 DB 조회 한 번이라 그렇게 오래
 * 기다릴 이유가 없다 -- 길게 잡으면 AI 가 멈췄을 때 콜백 스레드가 그만큼 붙잡힌다.
 */
@Configuration
@ConditionalOnProperty(name = "terms.matching.provider", havingValue = "ai")
public class TermsMatchClientConfig {

  @Bean("termsMatchRestClient")
  public RestClient termsMatchRestClient(
      RestClient.Builder builder,
      @Value("${ai.server.base-url}") String baseUrl,
      @Value("${ai.server.connect-timeout}") Duration connectTimeout,
      @Value("${ai.server.terms-match-read-timeout}") Duration readTimeout) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeout);
    requestFactory.setReadTimeout(readTimeout);
    return builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
  }
}
