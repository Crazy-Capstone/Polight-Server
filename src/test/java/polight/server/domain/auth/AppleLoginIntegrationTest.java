package polight.server.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import polight.server.domain.auth.apple.AppleTestServer;
import polight.server.domain.auth.client.AppleOAuthClient;
import polight.server.domain.auth.service.AppleTokenCipher;
import polight.server.domain.auth.service.RefreshTokenStore;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.repository.UserRepository;
import polight.server.domain.user.service.UserWithdrawalService;
import polight.server.global.security.JwtTokenProvider;

@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=${POLIGHT_APPLE_TEST_DB_URL:jdbc:h2:mem:apple-login;MODE=PostgreSQL;NON_KEYWORDS=VALUE}",
    "spring.datasource.driver-class-name=${POLIGHT_APPLE_TEST_DB_DRIVER:org.h2.Driver}",
    "spring.datasource.username=${POLIGHT_APPLE_TEST_DB_USER:sa}",
    "spring.datasource.password=",
    "spring.flyway.enabled=${POLIGHT_APPLE_TEST_FLYWAY:false}",
    "spring.jpa.hibernate.ddl-auto=${POLIGHT_APPLE_TEST_DDL:create-drop}",
    "spring.jpa.show-sql=false",
    "spring.docker.compose.enabled=false",
    "analysis.timeout.enabled=false",
    "storage.type=local",
    "terms.matching.provider=local",
    "oauth.kakao.client-id=test-client-id",
    "oauth.kakao.redirect-uri=http://localhost/test",
    "security.jwt.secret=test-secret-key-with-at-least-32-bytes"
})
@AutoConfigureMockMvc
@DirtiesContext
@ExtendWith(OutputCaptureExtension.class)
class AppleLoginIntegrationTest {

  private static final AppleTestServer APPLE = new AppleTestServer();
  private static final String ENDPOINT = "/api/auth/apple/login";
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private UserRepository users;
  @Autowired private JwtTokenProvider jwt;
  @Autowired private AppleTokenCipher cipher;
  @Autowired private UserWithdrawalService withdrawal;
  @Autowired private PlatformTransactionManager transactions;
  @MockitoBean private RefreshTokenStore refreshTokens;
  @MockitoSpyBean private AppleOAuthClient appleClient;
  private final AtomicInteger issued = new AtomicInteger();

  @DynamicPropertySource
  static void configureApple(DynamicPropertyRegistry registry) {
    var p = APPLE.properties();
    registry.add("oauth.apple.client-id", p::clientId);
    registry.add("oauth.apple.team-id", p::teamId);
    registry.add("oauth.apple.key-id", p::keyId);
    registry.add("oauth.apple.private-key-path", p::privateKeyPath);
    registry.add("oauth.apple.token-encryption-key", p::tokenEncryptionKey);
    registry.add("oauth.apple.jwks-uri", p::jwksUri);
    registry.add("oauth.apple.token-uri", p::tokenUri);
    registry.add("oauth.apple.revoke-uri", p::revokeUri);
    registry.add("oauth.apple.connect-timeout", () -> "1s");
    registry.add("oauth.apple.read-timeout", () -> "2s");
  }

  @AfterAll
  static void stopApple() {
    APPLE.close();
  }

  @BeforeEach
  void setUp() {
    APPLE.reset();
    issued.set(0);
    when(refreshTokens.issue(any())).thenAnswer(invocation -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      issued.incrementAndGet();
      return "polight-refresh-" + UUID.randomUUID();
    });
    doAnswer(invocation -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      return invocation.callRealMethod();
    }).when(appleClient).exchangeCode(anyString());
  }

  @Test
  void createsAppleUserWithoutEmailAndReturnsExistingServiceTokenContract() throws Exception {
    String subject = subject();
    String code = code();
    var response = login(subject, code, "nonce", "홍길동");
    User user = users.findByProviderAndProviderId(User.Provider.APPLE, subject).orElseThrow();
    assertThat(jwt.parseUserId(response.path("accessToken").asText())).isEqualTo(user.getId());
    assertThat(response.path("refreshToken").asText()).startsWith("polight-refresh-");
    assertThat(response.path("expiresInSeconds").asLong()).isEqualTo(3600);
    assertThat(response.path("nickname").asText()).isEqualTo("홍길동");
    assertThat(response.path("profileImageUrl").isNull()).isTrue();
    assertThat(user.getEmail()).isNull();
    assertThat(user.getAppleRefreshTokenEncrypted()).startsWith("v1.").doesNotContain("apple-refresh-");
    assertThat(cipher.decrypt(user.getAppleRefreshTokenEncrypted(), subject)).isEqualTo("apple-refresh-" + code);
    assertThat(mapper.writeValueAsString(user)).doesNotContain("appleRefreshTokenEncrypted", user.getAppleRefreshTokenEncrypted());
    assertThat(response.toString()).doesNotContain("apple-refresh-" + code);
    assertThat(issued).hasValue(1);
    assertThat(APPLE.tokenCalls).hasValue(1);
    assertThat(APPLE.lastTokenForm.get()).containsEntry("grant_type", "authorization_code")
        .containsEntry("client_id", "com.polight.app").containsEntry("code", code);
  }

  @Test
  void existingAppleUserKeepsIdAndNameWhenNameIsNullAndUpdatesRevocationToken() throws Exception {
    String subject = subject();
    var first = login(subject, code(), "first-nonce", "Original name");
    UUID id = jwt.parseUserId(first.path("accessToken").asText());
    String secondCode = code();
    var second = login(subject, secondCode, "second-nonce", null);
    assertThat(jwt.parseUserId(second.path("accessToken").asText())).isEqualTo(id);
    assertThat(second.path("nickname").asText()).isEqualTo("Original name");
    var user = users.findById(id).orElseThrow();
    assertThat(user.getEmail()).isNull();
    assertThat(cipher.decrypt(user.getAppleRefreshTokenEncrypted(), subject)).isEqualTo("apple-refresh-" + secondCode);
  }

  @Test
  void newUserWithoutNameUsesDefaultAndExistingNameCanBeExplicitlyChanged() throws Exception {
    String subject = subject();
    var first = login(subject, code(), "nonce", null);
    assertThat(first.path("nickname").asText()).isEqualTo("애플사용자");
    var second = login(subject, code(), "nonce", "Updated name");
    assertThat(second.path("nickname").asText()).isEqualTo("Updated name");
  }

  @Test
  void doesNotMergeAppleWithKakaoEvenWhenProviderIdsAndEmailsCouldMatch() throws Exception {
    String subject = subject();
    User kakao = users.saveAndFlush(User.builder().provider(User.Provider.KAKAO).providerId(subject)
        .name("Kakao").email("ignored-email@example.invalid").build());
    var result = login(subject, code(), "nonce", null);
    assertThat(jwt.parseUserId(result.path("accessToken").asText())).isNotEqualTo(kakao.getId());
    assertThat(users.findById(kakao.getId()).orElseThrow().getName()).isEqualTo("Kakao");
    assertThat(users.findByProviderAndProviderId(User.Provider.APPLE, subject).orElseThrow().getEmail()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"identityToken", "authorizationCode", "nonce"})
  void requiredFieldsAreValidatedBeforeAnyAppleRequest(String missing) throws Exception {
    var body = mapper.createObjectNode().put("identityToken", "placeholder").put("authorizationCode", "placeholder").put("nonce", "nonce");
    body.remove(missing);
    mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body.toString()))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    assertThat(APPLE.tokenCalls).hasValue(0);
    assertThat(APPLE.jwksCalls).hasValue(0);
    assertThat(issued).hasValue(0);
  }

  @ParameterizedTest
  @ValueSource(strings = {"issuer", "audience", "expired", "nonce"})
  void invalidIdentityIsRejectedBeforeCodeExchangeAndAccountCreation(String problem) throws Exception {
    String subject = subject();
    String token = APPLE.identity(subject, "nonce", builder -> {
      switch (problem) {
        case "issuer" -> builder.issuer("https://untrusted.example");
        case "audience" -> builder.audience("wrong.app");
        case "expired" -> builder.issueTime(Date.from(Instant.now().minusSeconds(600)))
            .expirationTime(Date.from(Instant.now().minusSeconds(1)));
        default -> builder.claim("nonce", "wrong-hash");
      }
    });
    mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body(token, code(), "nonce", null)))
        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("APPLE_IDENTITY_TOKEN_INVALID"));
    assertThat(APPLE.tokenCalls).hasValue(0);
    assertThat(users.findByProviderAndProviderId(User.Provider.APPLE, subject)).isEmpty();
    assertThat(issued).hasValue(0);
  }

  @Test
  void authorizationCodeIsAlwaysExchangedAndCannotBeReused() throws Exception {
    String subject = subject();
    String code = code();
    String token = APPLE.identity(subject, "nonce");
    APPLE.registerCode(code, subject, "nonce");
    String body = body(token, code, "nonce", null);
    mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("APPLE_AUTHORIZATION_CODE_INVALID"));
    assertThat(APPLE.tokenCalls).hasValue(2);
    assertThat(issued).hasValue(1);
  }

  @Test
  void exchangedIdentityMustMatchOriginalSubject() throws Exception {
    String subject = subject();
    String code = code();
    APPLE.registerCode(code, "different-subject", "nonce");
    mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
            .content(body(APPLE.identity(subject, "nonce"), code, "nonce", null)))
        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("APPLE_IDENTITY_TOKEN_INVALID"));
    assertThat(users.findByProviderAndProviderId(User.Provider.APPLE, subject)).isEmpty();
    assertThat(issued).hasValue(0);
  }

  @Test
  void missingExchangeIdentityFailsClosedAndMissingInitialRefreshCannotCreateAccount() throws Exception {
    String subject = subject();
    String code = code();
    APPLE.registerResponse(code, null, "apple-refresh");
    mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
            .content(body(APPLE.identity(subject, "nonce"), code, "nonce", null)))
        .andExpect(status().isBadGateway());
    String secondCode = code();
    APPLE.registerResponse(secondCode, APPLE.identity(subject, "nonce"), null);
    mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
            .content(body(APPLE.identity(subject, "nonce"), secondCode, "nonce", null)))
        .andExpect(status().isBadGateway());
    assertThat(users.findByProviderAndProviderId(User.Provider.APPLE, subject)).isEmpty();
    assertThat(issued).hasValue(0);
  }

  @Test
  void missingRefreshOnLaterLoginKeepsPreviouslyEncryptedToken() throws Exception {
    String subject = subject();
    login(subject, code(), "nonce", "Name");
    String original = users.findByProviderAndProviderId(User.Provider.APPLE, subject).orElseThrow().getAppleRefreshTokenEncrypted();
    String code = code();
    APPLE.registerResponse(code, APPLE.identity(subject, "nonce"), null);
    mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
            .content(body(APPLE.identity(subject, "nonce"), code, "nonce", null))).andExpect(status().isOk());
    assertThat(users.findByProviderAndProviderId(User.Provider.APPLE, subject).orElseThrow().getAppleRefreshTokenEncrypted()).isEqualTo(original);
  }

  @ParameterizedTest
  @ValueSource(strings = {"invalid_grant", "invalid_client", "server"})
  void distinguishesCodeFailureFromAppleServerOrConfigurationFailure(String problem, CapturedOutput output) throws Exception {
    String subject = subject();
    String code = code();
    String token = APPLE.identity(subject, "nonce");
    APPLE.tokenStatus.set("server".equals(problem) ? 503 : 400);
    APPLE.tokenError = problem;
    APPLE.errorDescription = token + code;
    var result = mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body(token, code, "nonce", null)));
    if ("invalid_grant".equals(problem)) {
      result.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("APPLE_AUTHORIZATION_CODE_INVALID"));
    } else {
      result.andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code")
          .value("server".equals(problem) ? "APPLE_SERVICE_UNAVAILABLE" : "APPLE_CONFIGURATION_ERROR"));
    }
    assertThat(users.findByProviderAndProviderId(User.Provider.APPLE, subject)).isEmpty();
    assertThat(issued).hasValue(0);
    assertThat(output.getAll()).doesNotContain(token, code);
  }

  @Test
  void concurrentDistinctCodesCreateOneAppleUser() throws Exception {
    String subject = subject();
    List<String> codes = List.of(code(), code());
    codes.forEach(code -> APPLE.registerCode(code, subject, "nonce"));
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var futures = codes.stream().map(code -> executor.submit(() -> {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return perform(body(APPLE.identity(subject, "nonce"), code, "nonce", null));
      })).toList();
      start.countDown();
      var first = futures.get(0).get(10, TimeUnit.SECONDS);
      var second = futures.get(1).get(10, TimeUnit.SECONDS);
      assertThat(jwt.parseUserId(first.path("accessToken").asText()))
          .isEqualTo(jwt.parseUserId(second.path("accessToken").asText()));
      assertThat(users.findByProviderAndProviderId(User.Provider.APPLE, subject)).isPresent();
      assertThat(issued).hasValue(2);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void appleAccessTokenWorksWithUnchangedConsentApis() throws Exception {
    var login = login(subject(), code(), "nonce", null);
    String authorization = "Bearer " + login.path("accessToken").asText();
    mvc.perform(get("/api/consents").header(HttpHeaders.AUTHORIZATION, authorization)).andExpect(status().isNotFound());
    mvc.perform(post("/api/consents").header(HttpHeaders.AUTHORIZATION, authorization)
            .contentType(MediaType.APPLICATION_JSON).content("{\"consents\":{\"TERMS_OF_SERVICE\":true}}"))
        .andExpect(status().isCreated());
    mvc.perform(get("/api/consents").header(HttpHeaders.AUTHORIZATION, authorization))
        .andExpect(status().isOk()).andExpect(jsonPath("$.consents.TERMS_OF_SERVICE").value(true));
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/consents")
            .header(HttpHeaders.AUTHORIZATION, authorization).contentType(MediaType.APPLICATION_JSON)
            .content("{\"consents\":{\"TERMS_OF_SERVICE\":false}}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.consents.TERMS_OF_SERVICE").value(false));
  }

  @Test
  void appleUserCanRefreshServiceTokensThroughExistingEndpoint() throws Exception {
    var initial = login(subject(), code(), "nonce", null);
    UUID id = jwt.parseUserId(initial.path("accessToken").asText());
    when(refreshTokens.consume(initial.path("refreshToken").asText())).thenReturn(id);
    var result = mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(java.util.Map.of("refreshToken", initial.path("refreshToken").asText()))))
        .andExpect(status().isOk()).andReturn();
    var refreshed = mapper.readTree(result.getResponse().getContentAsString());
    assertThat(jwt.parseUserId(refreshed.path("accessToken").asText())).isEqualTo(id);
    assertThat(refreshed.path("refreshToken")).isNotEqualTo(initial.path("refreshToken"));
    assertThat(refreshed.path("profileImageUrl").isNull()).isTrue();
    assertThat(issued).hasValue(2);
    assertThat(APPLE.tokenCalls).hasValue(1);
  }

  @Test
  void simultaneousReuseOfOneAuthorizationCodeAllowsOnlyOneLogin() throws Exception {
    String subject = subject();
    String code = code();
    APPLE.registerCode(code, subject, "nonce");
    String request = body(APPLE.identity(subject, "nonce"), code, "nonce", null);
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var futures = List.of(1, 2).stream().map(i -> executor.submit(() -> {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(request))
            .andReturn().getResponse().getStatus();
      })).toList();
      start.countDown();
      assertThat(List.of(futures.get(0).get(10, TimeUnit.SECONDS), futures.get(1).get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 401);
      assertThat(issued).hasValue(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void revokesAppleRefreshOnlyAfterCommittedAccountDeletion() throws Exception {
    String code = code();
    var login = login(subject(), code, "nonce", null);
    UUID id = jwt.parseUserId(login.path("accessToken").asText());
    doAnswer(invocation -> {
      assertThat(users.existsById(id)).isFalse();
      return invocation.callRealMethod();
    }).when(appleClient).revoke(anyString());
    mvc.perform(delete("/api/users/withdraw").header(HttpHeaders.AUTHORIZATION, "Bearer " + login.path("accessToken").asText()))
        .andExpect(status().isNoContent());
    assertThat(APPLE.revokeCalls).hasValue(1);
    assertThat(APPLE.lastRevokeForm.get()).containsEntry("token", "apple-refresh-" + code)
        .containsEntry("token_type_hint", "refresh_token");
    assertThat(users.existsById(id)).isFalse();
  }

  @Test
  void rollbackDoesNotRevokeAppleAuthorization() throws Exception {
    var login = login(subject(), code(), "nonce", null);
    UUID id = jwt.parseUserId(login.path("accessToken").asText());
    new TransactionTemplate(transactions).executeWithoutResult(status -> {
      withdrawal.withdraw(id);
      status.setRollbackOnly();
    });
    assertThat(users.existsById(id)).isTrue();
    assertThat(APPLE.revokeCalls).hasValue(0);
  }

  @Test
  void retriesTransientRevocationAndDoesNotKeepAccountOnPermanentOutage(CapturedOutput output) throws Exception {
    String code = code();
    var login = login(subject(), code, "nonce", null);
    UUID id = jwt.parseUserId(login.path("accessToken").asText());
    APPLE.revokeStatus.set(503);
    mvc.perform(delete("/api/users/withdraw").header(HttpHeaders.AUTHORIZATION, "Bearer " + login.path("accessToken").asText()))
        .andExpect(status().isNoContent());
    assertThat(users.existsById(id)).isFalse();
    assertThat(APPLE.revokeCalls).hasValue(2);
    assertThat(output.getAll()).doesNotContain("apple-refresh-" + code);
  }

  @Test
  void transientRevocationFailureIsRetriedOnce() throws Exception {
    var login = login(subject(), code(), "nonce", null);
    APPLE.revokeFailures.set(1);
    mvc.perform(delete("/api/users/withdraw").header(HttpHeaders.AUTHORIZATION, "Bearer " + login.path("accessToken").asText()))
        .andExpect(status().isNoContent());
    assertThat(APPLE.revokeCalls).hasValue(2);
  }

  @Test
  void kakaoWithdrawalDoesNotCallApple() throws Exception {
    User kakao = users.saveAndFlush(User.builder().provider(User.Provider.KAKAO).providerId(subject()).name("Kakao").build());
    mvc.perform(delete("/api/users/withdraw").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.createAccessToken(kakao)))
        .andExpect(status().isNoContent());
    assertThat(APPLE.revokeCalls).hasValue(0);
  }

  @Test
  void swaggerDocumentsAppleLoginWithoutServiceTokenRequirement() throws Exception {
    var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
    var docs = mapper.readTree(result.getResponse().getContentAsString());
    var endpoint = docs.path("paths").path(ENDPOINT).path("post");
    assertThat(endpoint.path("security")).isEmpty();
    assertThat(endpoint.path("responses").has("401")).isTrue();
    assertThat(endpoint.path("responses").has("503")).isTrue();
    var fields = docs.path("components").path("schemas").path("AppleLoginRequest").path("properties");
    assertThat(fields.has("email")).isFalse();
    assertThat(fields.has("identityToken")).isTrue();
    assertThat(fields.has("authorizationCode")).isTrue();
    assertThat(fields.has("nonce")).isTrue();
    assertThat(fields.has("name")).isTrue();
    assertThat(docs.path("paths").has("/api/auth/kakao/login")).isTrue();
  }

  private JsonNode login(String subject, String code, String nonce, String name) throws Exception {
    APPLE.registerCode(code, subject, nonce);
    return perform(body(APPLE.identity(subject, nonce), code, nonce, name));
  }

  private JsonNode perform(String body) throws Exception {
    var result = mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk()).andReturn();
    return mapper.readTree(result.getResponse().getContentAsString());
  }

  private String body(String token, String code, String nonce, String name) throws Exception {
    var body = mapper.createObjectNode().put("identityToken", token).put("authorizationCode", code).put("nonce", nonce);
    if (name != null) {
      body.put("name", name);
    } else {
      body.putNull("name");
    }
    return mapper.writeValueAsString(body);
  }

  private String subject() {
    return "apple-" + UUID.randomUUID();
  }

  private String code() {
    return "code-" + UUID.randomUUID();
  }
}
