package polight.server.domain.chat.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;
import polight.server.domain.chat.dto.RagQueryRequest;
import polight.server.domain.chat.dto.RagQueryResponse;
import polight.server.global.exception.BaseException;

class FastApiRagQueryClientTest {

  @Test
  void parsesAnswerAndSources() {
    String body =
        """
        {
          "answer": "4시간 이상 지연 시 보상됩니다.",
          "responseType": "TEXT",
          "sources": [
            {
              "chunkId": "11111111-1111-1111-1111-111111111111",
              "documentId": "22222222-2222-2222-2222-222222222222",
              "page": 12,
              "quote": "항공기 지연으로 인하여..."
            }
          ]
        }
        """;

    RagQueryResponse response = client(ok(body)).query(request());

    assertThat(response.answer()).isEqualTo("4시간 이상 지연 시 보상됩니다.");
    assertThat(response.responseType()).isEqualTo("TEXT");
    assertThat(response.sources()).hasSize(1);
    assertThat(response.sources().get(0).page()).isEqualTo(12);
  }

  @Test
  void retriesOnceWhenConnectionFails() {
    AtomicInteger attempts = new AtomicInteger();
    RestClient restClient =
        clientWith(
            () -> {
              if (attempts.incrementAndGet() == 1) {
                throw new IOException("connection refused");
              }
              return okResponse("{\"answer\":\"됩니다.\",\"responseType\":\"TEXT\",\"sources\":[]}");
            });

    assertThat(client(restClient).query(request()).answer()).isEqualTo("됩니다.");
    // AI 컨테이너 재시작 중일 때 바로 실패하지 않기 위한 1회 재시도다.
    assertThat(attempts).hasValue(2);
  }

  @Test
  void doesNotRetryServerError() {
    AtomicInteger attempts = new AtomicInteger();
    RestClient restClient =
        clientWith(
            () -> {
              attempts.incrementAndGet();
              return new MockClientHttpResponse(new byte[0], HttpStatus.INTERNAL_SERVER_ERROR);
            });

    assertThatThrownBy(() -> client(restClient).query(request())).isInstanceOf(BaseException.class);
    // 이미 오래 기다린 뒤이므로 같은 질문을 다시 보내지 않는다.
    assertThat(attempts).hasValue(1);
  }

  @Test
  void failsWhenBodyHasNoAnswer() {
    // 200인데 answer가 없으면 뒤에서 NPE가 된다. 원인을 가리지 않도록 클라이언트가 끊는다.
    assertThatThrownBy(() -> client(ok("{\"responseType\":\"TEXT\"}")).query(request()))
        .isInstanceOf(BaseException.class)
        .hasMessageContaining("AI 서버");
  }

  private FastApiRagQueryClient client(RestClient restClient) {
    return new FastApiRagQueryClient(restClient, "/internal/rag/query", "internal-key");
  }

  private RagQueryRequest request() {
    return new RagQueryRequest(
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        null,
        UUID.randomUUID(),
        "항공편 지연되면 보상돼요?",
        List.of(),
        List.of(),
        false,
        List.of());
  }

  private RestClient ok(String body) {
    return clientWith(() -> okResponse(body));
  }

  private static MockClientHttpResponse okResponse(String body) {
    MockClientHttpResponse response =
        new MockClientHttpResponse(body.getBytes(java.nio.charset.StandardCharsets.UTF_8), HttpStatus.OK);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    return response;
  }

  private RestClient clientWith(ResponseFactory responseFactory) {
    ClientHttpRequestFactory factory =
        (uri, method) ->
            new MockClientHttpRequest(method, uri) {
              @Override
              protected MockClientHttpResponse executeInternal() throws IOException {
                return responseFactory.create();
              }
            };
    return RestClient.builder().baseUrl("http://ai-server").requestFactory(factory).build();
  }

  @FunctionalInterface
  private interface ResponseFactory {
    MockClientHttpResponse create() throws IOException;
  }
}
