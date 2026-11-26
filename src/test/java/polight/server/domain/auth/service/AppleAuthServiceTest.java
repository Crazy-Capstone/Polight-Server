package polight.server.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import polight.server.domain.auth.client.AppleOAuthClient;
import polight.server.domain.auth.client.KakaoOAuthClient;
import polight.server.domain.auth.dto.AppleLoginRequest;
import polight.server.domain.auth.dto.apple.AppleTokenResponse;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.service.UserService;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;
import polight.server.global.security.JwtTokenProvider;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppleAuthServiceTest {

  @Mock private UserService users;
  @Mock private JwtTokenProvider jwt;
  @Mock private KakaoOAuthClient kakao;
  @Mock private RefreshTokenStore refresh;
  @Mock private AppleIdentityTokenVerifier verifier;
  @Mock private AppleOAuthClient apple;
  @Mock private AppleTokenCipher cipher;
  private AuthService service;
  private User user;
  private final AppleLoginRequest request = new AppleLoginRequest("front-identity", "code", "nonce", null);

  @BeforeEach
  void setUp() {
    service = new AuthService(users, jwt, kakao, refresh, verifier, apple, cipher);
    user = User.builder().provider(User.Provider.APPLE).providerId("verified-subject").name("Apple user").build();
    user.updateAppleCredentials(null, "encrypted-credential");
    ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    when(verifier.verify("front-identity", "nonce")).thenReturn("verified-subject");
    when(verifier.verify("exchanged-identity", "nonce")).thenReturn("verified-subject");
    when(apple.exchangeCode("code")).thenReturn(new AppleTokenResponse("apple-access", "apple-refresh", "exchanged-identity"));
    when(cipher.encrypt("apple-refresh", "verified-subject")).thenReturn("encrypted-credential");
    when(users.findAppleUserForLogin("verified-subject", null, "encrypted-credential")).thenReturn(Optional.of(user));
    when(jwt.createAccessToken(user)).thenReturn("service-access");
    when(jwt.getAccessTokenExpirySeconds()).thenReturn(3600L);
    when(refresh.issue(user.getId())).thenReturn("service-refresh");
  }

  @Test
  void invalidIdentityNeverQueriesUsersExchangesCodeOrIssuesTokens() {
    when(verifier.verify("front-identity", "nonce"))
        .thenThrow(new BaseException(ErrorCode.APPLE_IDENTITY_TOKEN_INVALID));
    assertError(ErrorCode.APPLE_IDENTITY_TOKEN_INVALID);
    verifyNoInteractions(users, apple, jwt, refresh, cipher);
  }

  @Test
  void codeFailureNeverQueriesUsersOrIssuesTokens() {
    when(apple.exchangeCode("code")).thenThrow(new BaseException(ErrorCode.APPLE_AUTHORIZATION_CODE_INVALID));
    assertError(ErrorCode.APPLE_AUTHORIZATION_CODE_INVALID);
    verifyNoInteractions(users, jwt, refresh);
    verify(cipher, never()).encrypt(any(), any());
  }

  @Test
  void subMismatchNeverQueriesUsersOrIssuesTokens() {
    when(verifier.verify("exchanged-identity", "nonce")).thenReturn("different-subject");
    assertError(ErrorCode.APPLE_IDENTITY_TOKEN_INVALID);
    verifyNoInteractions(users, jwt, refresh);
  }

  @Test
  void encryptionMisconfigurationDoesNotConsumeOneTimeCode() {
    org.mockito.Mockito.doThrow(new BaseException(ErrorCode.APPLE_CONFIGURATION_ERROR)).when(cipher).validateConfiguration();
    assertError(ErrorCode.APPLE_CONFIGURATION_ERROR);
    verifyNoInteractions(apple, users, jwt, refresh);
  }

  @Test
  void duplicateConcurrentCreationRequeriesTheWinningAppleAccount() {
    when(users.findAppleUserForLogin("verified-subject", null, "encrypted-credential"))
        .thenReturn(Optional.empty()).thenReturn(Optional.of(user));
    when(users.createAppleUser("verified-subject", null, "encrypted-credential"))
        .thenThrow(new DataIntegrityViolationException("Unique provider account"));
    var result = service.loginWithApple(request);
    assertThat(result.accessToken()).isEqualTo("service-access");
    verify(users, times(2)).findAppleUserForLogin("verified-subject", null, "encrypted-credential");
    verify(refresh).issue(user.getId());
  }

  @Test
  void credentialStorageFailurePreventsServiceTokenIssuance() {
    when(users.findAppleUserForLogin("verified-subject", null, "encrypted-credential"))
        .thenThrow(new IllegalStateException("Database unavailable"));
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> service.loginWithApple(request));
    verifyNoInteractions(jwt, refresh);
  }

  @Test
  @SuppressWarnings("unchecked")
  void usesExistingRefreshStoreToWriteOnlyHashedOpaqueTokenToRedis() throws Exception {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    var realStore = new RefreshTokenStore(redis, Duration.ofDays(14).toSeconds());
    var auth = new AuthService(users, jwt, kakao, realStore, verifier, apple, cipher);

    var result = auth.loginWithApple(request);
    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
        .digest(result.refreshToken().getBytes(StandardCharsets.UTF_8)));
    verify(values).set("auth:refresh:" + hash, user.getId().toString(), Duration.ofDays(14));
    assertThat(result.accessToken()).isEqualTo("service-access");
    assertThat(result.refreshToken()).isNotBlank().isNotEqualTo("apple-refresh");
    assertThat(result.profileImageUrl()).isNull();
  }

  private void assertError(ErrorCode code) {
    assertThatExceptionOfType(BaseException.class).isThrownBy(() -> service.loginWithApple(request))
        .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(code));
  }
}
