package polight.server.domain.auth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;
import polight.server.domain.auth.apple.AppleTestServer;
import polight.server.domain.auth.service.AppleClientSecretGenerator;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

class AppleOAuthClientTest {

  private static final AppleTestServer APPLE = new AppleTestServer();
  private AppleOAuthClient client;

  @BeforeEach
  void setUp() {
    APPLE.reset();
    client = new AppleOAuthClient(APPLE.properties(), new AppleClientSecretGenerator(APPLE.properties()),
        RestClient.builder(), new ObjectMapper());
  }

  @AfterAll
  static void stop() {
    APPLE.close();
  }

  @Test
  void exchangesCodeOnceWithSignedClientSecretAndNeverSendsNativeRedirectUri() {
    String code = UUID.randomUUID().toString();
    APPLE.registerCode(code, "subject", "nonce");
    var response = client.exchangeCode(code);
    assertThat(response.refreshToken()).isEqualTo("apple-refresh-" + code);
    assertThat(response.identityToken()).isNotBlank();
    assertThat(APPLE.lastTokenForm.get()).containsEntry("client_id", "com.polight.app")
        .containsEntry("grant_type", "authorization_code").containsEntry("code", code)
        .doesNotContainKey("redirect_uri");
    assertThat(APPLE.tokenCalls).hasValue(1);
  }

  @Test
  void neverRetriesAuthorizationCodeExchangeOnAppleOutage() {
    APPLE.tokenStatus.set(503);
    assertThatExceptionOfType(BaseException.class).isThrownBy(() -> client.exchangeCode("code"))
        .satisfies(error -> {
          assertThat(error.getErrorCode()).isEqualTo(ErrorCode.APPLE_SERVICE_UNAVAILABLE);
          assertThat(error.getCause()).isNull();
        });
    assertThat(APPLE.tokenCalls).hasValue(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"invalid_grant", "invalid_request"})
  void classifiesInvalidCodeAs401WithoutLeakingAppleBody(String code) {
    APPLE.tokenStatus.set(400);
    APPLE.tokenError = code;
    APPLE.errorDescription = "sensitive-response-content";
    assertThatExceptionOfType(BaseException.class).isThrownBy(() -> client.exchangeCode("code"))
        .satisfies(error -> {
          assertThat(error.getErrorCode()).isEqualTo(ErrorCode.APPLE_AUTHORIZATION_CODE_INVALID);
          assertThat(error.getMessage()).doesNotContain("sensitive-response-content");
          assertThat(error.getCause()).isNull();
        });
  }

  @Test
  void repeatedRevocationIsIdempotentAndUsesRefreshTokenHint() {
    client.revoke("apple-test-refresh");
    client.revoke("apple-test-refresh");
    assertThat(APPLE.revokeCalls).hasValue(2);
    assertThat(APPLE.lastRevokeForm.get()).containsEntry("token", "apple-test-refresh")
        .containsEntry("token_type_hint", "refresh_token");
  }
}
