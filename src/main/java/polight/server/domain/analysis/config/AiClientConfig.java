package polight.server.domain.analysis.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class AiClientConfig {

  @Bean("aiRestClient")
  public RestClient aiRestClient(
      RestClient.Builder builder,
      @Value("${ai.server.base-url}") String baseUrl,
      @Value("${ai.server.connect-timeout}") Duration connectTimeout,
      @Value("${ai.server.read-timeout}") Duration readTimeout) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeout);
    requestFactory.setReadTimeout(readTimeout);
    return builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
  }
}
