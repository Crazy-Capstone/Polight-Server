package polight.server.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import polight.server.domain.auth.client.KakaoOAuthClient;
import polight.server.domain.auth.dto.kakao.KakaoTokenResponse;
import polight.server.domain.auth.dto.kakao.KakaoUserInfoResponse;
import polight.server.domain.consent.dto.ConsentDecision;
import polight.server.domain.consent.entity.ConsentHistory;
import polight.server.domain.consent.entity.ConsentType;
import polight.server.domain.consent.repository.ConsentHistoryRepository;
import polight.server.domain.consent.service.ConsentService;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.repository.UserRepository;
import polight.server.global.security.JwtTokenProvider;

@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=${POLIGHT_CONSENT_TEST_DB_URL:jdbc:h2:mem:signup-consents;MODE=PostgreSQL;NON_KEYWORDS=VALUE}",
    "spring.datasource.driver-class-name=${POLIGHT_CONSENT_TEST_DB_DRIVER:org.h2.Driver}",
    "spring.datasource.username=${POLIGHT_CONSENT_TEST_DB_USER:sa}",
    "spring.datasource.password=",
    "spring.flyway.enabled=${POLIGHT_CONSENT_TEST_FLYWAY:false}",
    "spring.jpa.hibernate.ddl-auto=${POLIGHT_CONSENT_TEST_DDL:create-drop}",
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
class KakaoSignupConsentIntegrationTest {

  private static final AtomicLong PROVIDER_IDS = new AtomicLong(1000);
  private static final String LOGIN = "/api/auth/kakao/login";
  private static final String SIGNUP = "/api/auth/kakao/signup";
  private static final String CONSENTS = "/api/v1/users/me/consents";
  private static final List<ConsentDecision> REQUIRED = List.of(
      new ConsentDecision(ConsentType.TERMS_OF_SERVICE, "dev-v1", true),
      new ConsentDecision(ConsentType.PRIVACY_COLLECTION_USE, "dev-v1", true),
      new ConsentDecision(ConsentType.AGE_14_PLUS, "dev-v1", true));

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private UserRepository users;
  @Autowired private ConsentHistoryRepository histories;
  @Autowired private JwtTokenProvider jwt;
  @MockitoBean private KakaoOAuthClient kakao;
  @MockitoBean private RefreshTokenStore refreshTokens;
  @MockitoBean private StringRedisTemplate redis;
  @MockitoBean private ValueOperations<String, String> values;
  @MockitoSpyBean private ConsentService consentService;

  private final Map<String, String> signupTokens = new ConcurrentHashMap<>();
  private long providerId;

  @BeforeEach
  void setUp() {
    signupTokens.clear();
    providerId = PROVIDER_IDS.incrementAndGet();
    when(redis.opsForValue()).thenReturn(values);
    doAnswer(invocation -> {
      String key = invocation.getArgument(0);
      assertThat(key).matches("auth:signup:[0-9a-f]{64}");
      assertThat((Duration) invocation.getArgument(2)).isEqualTo(Duration.ofMinutes(5));
      signupTokens.put(key, invocation.getArgument(1));
      return null;
    }).when(values).set(anyString(), anyString(), any(Duration.class));
    when(values.getAndDelete(anyString())).thenAnswer(invocation ->
        signupTokens.remove(invocation.getArgument(0, String.class)));

    when(kakao.requestToken(anyString())).thenReturn(
        new KakaoTokenResponse("bearer", "verified-access", null, 3600L, null));
    when(kakao.requestUserInfo("verified-access")).thenReturn(new KakaoUserInfoResponse(
        providerId, new KakaoUserInfoResponse.KakaoAccount("verified@example.test",
            new KakaoUserInfoResponse.Profile("Verified", "https://example.test/profile"))));
    when(refreshTokens.issue(any())).thenReturn("refresh");
  }

  @Test
  void newMemberReceivesNoServiceTokenUntilSignupCommits() throws Exception {
    String token = beginSignup();
    assertThat(findKakaoUser()).isEmpty();
    verifyNoInteractions(refreshTokens);

    when(refreshTokens.issue(any())).thenAnswer(invocation -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      UUID id = invocation.getArgument(0);
      assertThat(histories.findByUserIdOrderByIdDesc(id)).hasSize(3);
      return "refresh";
    });

    MvcResult result = submit(token, REQUIRED)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profileImageUrl").value("https://example.test/profile"))
        .andExpect(jsonPath("$.refreshToken").value("refresh"))
        .andReturn();
    String access = mapper.readTree(result.getResponse().getContentAsString()).path("accessToken").asText();
    assertThat(findKakaoUser().orElseThrow().getEmail()).isEqualTo("verified@example.test");

    mvc.perform(get(CONSENTS).header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allRequiredAgreed").value(true))
        .andExpect(jsonPath("$.allAgreed").value(false));

    submit(token, REQUIRED).andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_SIGNUP_TOKEN"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing", "declined", "duplicate", "old-version"})
  void invalidSignupDoesNotCreateAccountOrConsumeToken(String scenario) throws Exception {
    String token = beginSignup();
    var decisions = switch (scenario) {
      case "missing" -> REQUIRED.subList(0, 2);
      case "declined" -> List.of(REQUIRED.get(0), REQUIRED.get(1),
          new ConsentDecision(ConsentType.AGE_14_PLUS, "dev-v1", false));
      case "duplicate" -> List.of(REQUIRED.get(0), REQUIRED.get(0), REQUIRED.get(1), REQUIRED.get(2));
      default -> List.of(REQUIRED.get(0), REQUIRED.get(1),
          new ConsentDecision(ConsentType.AGE_14_PLUS, "old", true));
    };
    String expected = switch (scenario) {
      case "missing", "declined" -> "REQUIRED_CONSENT_NOT_AGREED";
      default -> "INVALID_INPUT";
    };

    submit(token, decisions).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(expected));
    assertThat(findKakaoUser()).isEmpty();
    verifyNoInteractions(refreshTokens);
    submit(token, REQUIRED).andExpect(status().isOk());
  }

  @Test
  void storageFailureRollsBackAccountAndAlreadyInsertedHistory() throws Exception {
    String token = beginSignup();
    var insertedId = new AtomicReference<UUID>();
    doAnswer(invocation -> {
      invocation.callRealMethod();
      User user = invocation.getArgument(0);
      insertedId.set(user.getId());
      assertThat(histories.findByUserIdOrderByIdDesc(user.getId())).hasSize(3);
      throw new IllegalStateException("test storage failure");
    }).when(consentService).recordConsents(any(), any());

    submit(token, REQUIRED).andExpect(status().isInternalServerError());
    assertThat(findKakaoUser()).isEmpty();
    assertThat(insertedId.get()).isNotNull();
    assertThat(histories.findByUserIdOrderByIdDesc(insertedId.get())).isEmpty();
    verifyNoInteractions(refreshTokens);
    submit(token, REQUIRED).andExpect(status().isUnauthorized());
  }

  @Test
  void existingMemberCanLoginWithNoConsentHistory() throws Exception {
    User user = users.save(User.builder().provider(User.Provider.KAKAO)
        .providerId(Long.toString(providerId)).name("Existing").build());

    login().andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isString())
        .andExpect(jsonPath("$.nickname").value("Verified"))
        .andExpect(jsonPath("$.signupToken").doesNotExist());
    assertThat(histories.findByUserIdOrderByIdDesc(user.getId())).isEmpty();
    assertThat(signupTokens).isEmpty();
  }

  @Test
  void expiredSignupTokenDoesNotCreateUser() throws Exception {
    String token = beginSignup();
    signupTokens.clear();

    submit(token, REQUIRED).andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_SIGNUP_TOKEN"));
    assertThat(findKakaoUser()).isEmpty();
    verifyNoInteractions(refreshTokens);
  }

  @Test
  void concurrentIndependentSignupTokensCreateOnlyOneAccount() throws Exception {
    String first = beginSignup();
    String second = beginSignup();
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var responses = List.of(first, second).stream().map(token -> executor.submit(() -> {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return submit(token, REQUIRED).andReturn().getResponse();
      })).toList();
      start.countDown();
      var a = responses.get(0).get(10, TimeUnit.SECONDS);
      var b = responses.get(1).get(10, TimeUnit.SECONDS);
      assertThat(List.of(a.getStatus(), b.getStatus())).containsExactlyInAnyOrder(200, 409);
      assertThat(mapper.readTree((a.getStatus() == 409 ? a : b).getContentAsString())
          .path("code").asText()).isEqualTo("KAKAO_USER_ALREADY_REGISTERED");
      assertThat(histories.findByUserIdOrderByIdDesc(findKakaoUser().orElseThrow().getId())).hasSize(3);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void consentEndpointsUseJwtIdentityAndPreserveDuplicateAndWithdrawalHistory() throws Exception {
    User owner = member();
    User other = member();
    String auth = "Bearer " + jwt.createAccessToken(owner);
    mvc.perform(get(CONSENTS)).andExpect(status().isUnauthorized());
    mvc.perform(post(CONSENTS).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get(CONSENTS).header(HttpHeaders.AUTHORIZATION, auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allRequiredAgreed").value(false))
        .andExpect(jsonPath("$.notAgreedTypes.length()").value(7));

    for (boolean agreed : List.of(true, true, false)) {
      var body = mapper.createObjectNode().put("userId", other.getId().toString());
      body.putArray("decisions").addObject().put("consentType", "OVERSEAS_TRANSFER")
          .put("documentVersion", "dev-v1").put("agreed", agreed);
      mvc.perform(post(CONSENTS).header(HttpHeaders.AUTHORIZATION, auth)
              .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.consents.OVERSEAS_TRANSFER").value(agreed));
    }

    assertThat(histories.findByUserIdOrderByIdDesc(owner.getId()))
        .extracting(ConsentHistory::isAgreed).containsExactly(false, true, true);
    assertThat(histories.findByUserIdOrderByIdDesc(other.getId())).isEmpty();
  }

  @Test
  void consentChangeRejectsMalformedDecisionsAndRequiredWithdrawalBeforeAnyWrite() throws Exception {
    User user = member();
    String auth = "Bearer " + jwt.createAccessToken(user);
    for (String body : List.of("null", "{}", "{\"decisions\":[]}", "{\"decisions\":[null]}",
        "{\"decisions\":[{\"consentType\":\"WRONG\",\"documentVersion\":\"dev-v1\",\"agreed\":true}]}",
        mapper.writeValueAsString(Map.of("decisions", List.of(REQUIRED.get(0), REQUIRED.get(0)))),
        mapper.writeValueAsString(Map.of("decisions", List.of(
            new ConsentDecision(ConsentType.TERMS_OF_SERVICE, "old", true),
            new ConsentDecision(ConsentType.HEALTH_INFORMATION, "dev-v1", null)))))) {
      mvc.perform(post(CONSENTS).header(HttpHeaders.AUTHORIZATION, auth)
              .contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
    mvc.perform(post(CONSENTS).header(HttpHeaders.AUTHORIZATION, auth)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                "decisions", List.of(new ConsentDecision(ConsentType.TERMS_OF_SERVICE, "dev-v1", false),
                    new ConsentDecision(ConsentType.OVERSEAS_TRANSFER, "dev-v1", true))))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("REQUIRED_CONSENT_WITHDRAWAL_NOT_SUPPORTED"));
    assertThat(histories.findByUserIdOrderByIdDesc(user.getId())).isEmpty();
  }

  @Test
  void concurrentConsentChangesReturnTheirOwnSavedStateAndKeepBothDecisions() throws Exception {
    User user = member();
    var executor = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    try {
      var futures = List.of(true, false).stream().map(agreed -> executor.submit(() -> {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        var result = consentService.changeConsents(user, List.of(
            new ConsentDecision(ConsentType.OVERSEAS_TRANSFER, "dev-v1", agreed)));
        assertThat(result.consents().get(ConsentType.OVERSEAS_TRANSFER)).isEqualTo(agreed);
        return result;
      })).toList();
      start.countDown();
      for (var future : futures) {
        future.get(10, TimeUnit.SECONDS);
      }
      var history = histories.findByUserIdOrderByIdDesc(user.getId());
      assertThat(history).extracting(ConsentHistory::isAgreed).containsExactlyInAnyOrder(true, false);
      assertThat(consentService.getStatus(user).consents().get(ConsentType.OVERSEAS_TRANSFER))
          .isEqualTo(history.get(0).isAgreed());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void swaggerExposesSignupConflictAndConsentOperations() throws Exception {
    var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
    var docs = mapper.readTree(result.getResponse().getContentAsString());
    var paths = docs.path("paths");
    assertThat(paths.path(LOGIN).path("post").path("responses").path("409")
        .path("content").findValue("$ref").asText()).endsWith("/SignupRequiredResponse");
    assertThat(paths.path(SIGNUP).path("post").path("security").isArray()).isTrue();
    assertThat(paths.path(SIGNUP).path("post").path("security").size()).isZero();
    assertThat(paths.path(CONSENTS).has("get")).isTrue();
    assertThat(paths.path(CONSENTS).has("post")).isTrue();
  }

  private ResultActions login() throws Exception {
    return mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
        .content("{\"authorizationCode\":\"code\"}"));
  }

  private String beginSignup() throws Exception {
    var result = login().andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SIGNUP_REQUIRED"))
        .andExpect(jsonPath("$.expiresInSeconds").value(300))
        .andExpect(jsonPath("$.accessToken").doesNotExist())
        .andExpect(jsonPath("$.refreshToken").doesNotExist())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andReturn();
    return mapper.readTree(result.getResponse().getContentAsString()).path("signupToken").asText();
  }

  private ResultActions submit(
      String token, List<ConsentDecision> decisions) throws Exception {
    return mvc.perform(post(SIGNUP).contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(Map.of("signupToken", token, "decisions", decisions))));
  }

  private Optional<User> findKakaoUser() {
    return users.findByProviderAndProviderId(User.Provider.KAKAO, Long.toString(providerId));
  }

  private User member() {
    return users.save(User.builder().provider(User.Provider.KAKAO)
        .providerId(UUID.randomUUID().toString()).name("Member").build());
  }
}
