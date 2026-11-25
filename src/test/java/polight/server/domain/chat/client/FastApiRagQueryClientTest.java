package polight.server.domain.chat.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;
import polight.server.domain.chat.dto.RagQueryRequest;
import polight.server.domain.chat.dto.RagQueryResponse;
import polight.server.domain.chat.dto.CertificateStatus;
import polight.server.global.exception.BaseException;

class FastApiRagQueryClientTest {

  @Test
  void receivesClaimDocumentsAndGuidanceWithoutRequiringEvidence() {
    String body = """
        {
          "answer": "일반적인 서류 안내",
          "responseType": "TEXT",
          "claimDocuments": [{
            "documentName": "보험금청구서",
            "requirementType": "REQUIRED",
            "coverageTitle": "공통",
            "sourceChunkId": null,
            "sourceQuote": null,
            "sectionTitle": null,
            "pageStart": null,
            "pageEnd": null
          }],
          "claimDocumentsNotice": "  일반적인 안내입니다.  ",
          "claimDocumentsUrl": "https://example.com/claim-documents?lang=ko#required"
        }
        """;

    var response = client(ok(body)).query(request());

    assertThat(response.claimDocumentsNotice()).isEqualTo("  일반적인 안내입니다.  ");
    assertThat(response.claimDocumentsUrl())
        .isEqualTo("https://example.com/claim-documents?lang=ko#required");
    assertThat(response.claimDocuments()).singleElement().satisfies(document -> {
      assertThat(document.documentName()).isEqualTo("보험금청구서");
      assertThat(document.sourceChunkId()).isNull();
      assertThat(document.sourceQuote()).isNull();
      assertThat(document.sectionTitle()).isNull();
      assertThat(document.pageStart()).isNull();
      assertThat(document.pageEnd()).isNull();
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {"", ",\"claimDocumentsNotice\":null,\"claimDocumentsUrl\":null"})
  void acceptsOldAndNullGuidanceResponses(String fields) {
    var response = client(ok("{\"answer\":\"Answer\",\"claimDocuments\":[]" + fields + "}")).query(request());

    assertThat(response.answer()).isEqualTo("Answer");
    assertThat(response.claimDocuments()).isEmpty();
    assertThat(response.claimDocumentsNotice()).isNull();
    assertThat(response.claimDocumentsUrl()).isNull();
  }

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
  void parsesNewSourceChunkFields() {
    String body =
        """
        {
          "answer": "최대 19만원까지 보상됩니다.",
          "responseType": "TEXT",
          "suggestedContacts": ["POLICE"],
          "sources": [
            {
              "chunkId": "11111111-1111-1111-1111-111111111111",
              "documentId": "22222222-2222-2222-2222-222222222222",
              "index": 1,
              "sectionTitle": "휴대품손해 특별약관",
              "page": 51,
              "pageStart": 51,
              "pageEnd": 52,
              "clauseType": "COVERAGE",
              "text": "제1조(보상하는 손해) ① 회사는 피보험자가 해외여행 도중에 …",
              "quote": "보험가입금액 한도 내에서",
              "cited": true
            }
          ]
        }
        """;

    RagQueryResponse.Source source = client(ok(body)).query(request()).sources().get(0);

    assertThat(source.index()).isEqualTo(1);
    assertThat(source.sectionTitle()).isEqualTo("휴대품손해 특별약관");
    assertThat(source.pageStart()).isEqualTo(51);
    assertThat(source.pageEnd()).isEqualTo(52);
    assertThat(source.clauseType()).isEqualTo("COVERAGE");
    assertThat(source.text()).startsWith("제1조(보상하는 손해)");
    assertThat(source.cited()).isTrue();
    // page/quote 는 새 필드가 생긴 뒤에도 그대로 온다.
    assertThat(source.page()).isEqualTo(51);
    assertThat(source.quote()).isEqualTo("보험가입금액 한도 내에서");
  }

  @Test
  void ignoresFieldsTheBackendDoesNotKnowYet() {
    String body =
        """
        {
          "answer": "보상됩니다.",
          "responseType": "TEXT",
          "sources": [
            {
              "chunkId": "11111111-1111-1111-1111-111111111111",
              "quote": "인용",
              "confidence": 0.93,
              "rerankScore": 12.5
            }
          ],
          "debugTrace": {"retrievedCount": 8}
        }
        """;

    // AI가 먼저 배포해도 챗봇이 500 이 되지 않아야 한다. 모르는 필드는 조용히 버린다.
    assertThat(client(ok(body)).query(request()).sources()).hasSize(1);
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

  @ParameterizedTest
  @EnumSource(CertificateStatus.class)
  void serializesCertificateStatusAndPreservesExistingRequestFields(CertificateStatus status) {
    var captured = new AtomicReference<JsonNode>();
    ClientHttpRequestFactory factory = (uri, method) -> new MockClientHttpRequest(method, uri) {
      @Override
      protected MockClientHttpResponse executeInternal() throws IOException {
        captured.set(new ObjectMapper().readTree(getBodyAsString()));
        return okResponse("{\"answer\":\"State guidance\",\"responseType\":\"TEXT\"}");
      }
    };
    var old = request();
    var request = new RagQueryRequest(old.userId(), old.tripId(), null, old.documentId(),
        old.policyId(), old.sessionId(), old.question(), old.history(), old.coverages(),
        old.coveragesComplete(), old.clausePaths(), status);

    client(RestClient.builder().baseUrl("http://ai-server").requestFactory(factory).build())
        .query(request);

    JsonNode sent = captured.get();
    assertThat(sent.path("certificateStatus").asText()).isEqualTo(status.name());
    assertThat(sent.path("termsId").isNull()).isTrue();
    assertThat(sent.path("userId").asText()).isEqualTo(old.userId().toString());
    assertThat(sent.path("tripId").asText()).isEqualTo(old.tripId().toString());
    assertThat(sent.path("sessionId").asText()).isEqualTo(old.sessionId().toString());
    assertThat(sent.path("question").asText()).isEqualTo(old.question());
    assertThat(sent.has("documentId")).isTrue();
    assertThat(sent.has("policyId")).isTrue();
    assertThat(sent.has("history")).isTrue();
    assertThat(sent.has("coverages")).isTrue();
    assertThat(sent.has("coveragesComplete")).isTrue();
    assertThat(sent.has("clausePaths")).isTrue();
  }

  private RagQueryRequest request() {
    return new RagQueryRequest(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        null,
        UUID.randomUUID(),
        "항공편 지연되면 보상돼요?",
        List.of(),
        List.of(),
        false,
        List.of(),
        CertificateStatus.COMPLETED);
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
