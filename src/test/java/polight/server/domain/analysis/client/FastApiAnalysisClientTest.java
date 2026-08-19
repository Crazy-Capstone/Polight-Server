package polight.server.domain.analysis.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;
import polight.server.domain.analysis.dto.AiAnalysisRequest;
import polight.server.global.exception.BaseException;

class FastApiAnalysisClientTest {

  @Test
  void retriesServerErrorsAndEventuallySucceeds() {
    AtomicInteger attempts = new AtomicInteger();
    RestClient restClient =
        clientWith(
            request ->
                new MockClientHttpResponse(
                    new byte[0],
                    attempts.incrementAndGet() < 3 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.ACCEPTED));
    FastApiAnalysisClient client = client(restClient, 3);

    client.requestAnalysis(request());

    org.assertj.core.api.Assertions.assertThat(attempts).hasValue(3);
  }

  @Test
  void doesNotRetryNonRateLimitedClientError() {
    AtomicInteger attempts = new AtomicInteger();
    RestClient restClient =
        clientWith(
            request -> {
              attempts.incrementAndGet();
              return new MockClientHttpResponse(new byte[0], HttpStatus.BAD_REQUEST);
            });

    assertThatThrownBy(() -> client(restClient, 3).requestAnalysis(request()))
        .isInstanceOf(BaseException.class);
    org.assertj.core.api.Assertions.assertThat(attempts).hasValue(1);
  }

  @Test
  void failsAfterConfiguredAttempts() {
    AtomicInteger attempts = new AtomicInteger();
    RestClient restClient =
        clientWith(
            request -> {
              attempts.incrementAndGet();
              return new MockClientHttpResponse(new byte[0], HttpStatus.INTERNAL_SERVER_ERROR);
            });

    assertThatThrownBy(() -> client(restClient, 2).requestAnalysis(request()))
        .isInstanceOf(BaseException.class)
        .hasMessageContaining("AI 서버");
    org.assertj.core.api.Assertions.assertThat(attempts).hasValue(2);
  }

  private FastApiAnalysisClient client(RestClient restClient, int maxAttempts) {
    return new FastApiAnalysisClient(
        restClient, "/internal/analyses", "internal-key", maxAttempts, Duration.ZERO);
  }

  private AiAnalysisRequest request() {
    return new AiAnalysisRequest(UUID.randomUUID(), "https://example.com/presigned");
  }

  private RestClient clientWith(ResponseFactory responseFactory) {
    ClientHttpRequestFactory factory =
        (uri, method) ->
            new MockClientHttpRequest(method, uri) {
              @Override
              protected MockClientHttpResponse executeInternal() {
                return responseFactory.create(this);
              }
            };
    return RestClient.builder().baseUrl("http://ai-server").requestFactory(factory).build();
  }

  @FunctionalInterface
  private interface ResponseFactory {
    MockClientHttpResponse create(ClientHttpRequest request);
  }
}
