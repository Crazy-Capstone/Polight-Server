package polight.server.domain.terms.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;
import polight.server.domain.terms.dto.TermsMatchRequest;
import polight.server.domain.terms.dto.TermsMatchResponse;
import polight.server.global.exception.BaseException;

class FastApiTermsMatchClientTest {

  @Test
  void parsesMatchedResponse() {
    String body =
        """
        {
          "termsId": "11111111-1111-1111-1111-111111111111",
          "level": "EXACT",
          "notice": null,
          "insurerName": "삼성화재",
          "productName": "해외여행보험",
          "revision": "2026-06-06"
        }
        """;

    TermsMatchResponse response = client(ok(body)).match(request());

    assertThat(response.termsId()).hasToString("11111111-1111-1111-1111-111111111111");
    assertThat(response.level()).isEqualTo("EXACT");
    assertThat(response.revision()).isEqualTo("2026-06-06");
  }

  @Test
  void treatsNullTermsIdAsNotFound() {
    // 합의한 계약. 못 찾는 것은 오류가 아니라 정상 갈래라 200 으로 온다.
    String body = "{\"termsId\": null, \"level\": \"NONE\", \"notice\": \"등록된 약관이 없습니다.\"}";

    assertThat(client(ok(body)).match(request()).termsId()).isNull();
  }

  @Test
  void treatsNotFoundStatusAsNotFoundToo() {
    // 404 로 돌려주는 것도 흔한 선택이라 같은 뜻으로 받는다. 오류로 다루면 정상 갈래인
    // "약관 미등록"이 전부 재시도 대상이 된다.
    TermsMatchResponse response =
        client(status(HttpStatus.NOT_FOUND)).match(request());

    assertThat(response.termsId()).isNull();
    assertThat(response.level()).isEqualTo("NONE");
  }

  @Test
  void failsOnServerError() {
    // 이건 "못 찾았다"가 아니라 "못 물어봤다"다. 호출부가 기존 연결을 지우지 않도록 구분해야 한다.
    assertThatThrownBy(() -> client(status(HttpStatus.INTERNAL_SERVER_ERROR)).match(request()))
        .isInstanceOf(BaseException.class)
        .hasMessageContaining("약관 매칭");
  }

  @Test
  void ignoresFieldsTheBackendDoesNotKnowYet() {
    String body =
        """
        {
          "termsId": "11111111-1111-1111-1111-111111111111",
          "level": "EXACT",
          "score": 0.95,
          "matchedBy": "policy_code"
        }
        """;

    // AI 가 필드를 늘려도 매칭이 멈추지 않아야 한다.
    assertThat(client(ok(body)).match(request()).termsId()).isNotNull();
  }

  private FastApiTermsMatchClient client(RestClient restClient) {
    return new FastApiTermsMatchClient(restClient, "/internal/terms/match", "internal-key");
  }

  private TermsMatchRequest request() {
    return new TermsMatchRequest(
        "삼성화재해상보험주식회사", "해외여행보험 (Travel Overseas)", LocalDate.of(2026, 7, 30));
  }

  private RestClient ok(String body) {
    return clientWith(
        () -> {
          MockClientHttpResponse response =
              new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
          response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
          return response;
        });
  }

  private RestClient status(HttpStatus status) {
    return clientWith(() -> new MockClientHttpResponse(new byte[0], status));
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
