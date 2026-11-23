package polight.server.domain.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import polight.server.domain.auth.client.KakaoOAuthClient;
import polight.server.domain.auth.dto.kakao.KakaoTokenResponse;
import polight.server.domain.auth.dto.kakao.KakaoUserInfoResponse;
import polight.server.domain.auth.service.RefreshTokenStore;
import polight.server.domain.consent.entity.ConsentType;
import polight.server.domain.consent.repository.ConsentHistoryRepository;
import polight.server.domain.consent.repository.ConsentRepository;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.repository.UserRepository;
import polight.server.global.security.JwtTokenProvider;

@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=${POLIGHT_CONSENT_TEST_DB_URL:jdbc:h2:mem:consent-http;MODE=PostgreSQL;NON_KEYWORDS=VALUE}",
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
class ConsentHttpTest {

  private static final String ENDPOINT = "/api/consents";
  private static final AtomicLong PROVIDERS = new AtomicLong(100000);

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private UserRepository users;
  @Autowired private ConsentRepository consents;
  @Autowired private ConsentHistoryRepository histories;
  @Autowired private JwtTokenProvider jwt;
  @MockitoBean private KakaoOAuthClient kakao;
  @MockitoBean private RefreshTokenStore refresh;
  private User user;
  private String authorization;

  @BeforeEach
  void createUser() {
    user = users.saveAndFlush(User.builder().provider(User.Provider.KAKAO)
        .providerId(UUID.randomUUID().toString()).name("Tester").build());
    authorization = "Bearer " + jwt.createAccessToken(user);
  }

  @Test
  void createsReadsAndPartiallyUpdatesAllSevenValues() throws Exception {
    var create = mvc.perform(post(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization)
            .contentType(MediaType.APPLICATION_JSON).content(body(false).toString()))
        .andExpect(status().isCreated())
        .andExpect(header().string(HttpHeaders.LOCATION, ENDPOINT))
        .andExpect(jsonPath("$.consents.length()").value(7))
        .andExpect(jsonPath("$.consents.TERMS_OF_SERVICE").value(false))
        .andExpect(jsonPath("$.createdAt").isString())
        .andExpect(jsonPath("$.updatedAt").isString())
        .andReturn();
    var initial = mapper.readTree(create.getResponse().getContentAsString());
    mvc.perform(post(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization)
            .contentType(MediaType.APPLICATION_JSON).content(body(true).toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONSENT_ALREADY_EXISTS"));
    var updated = mvc.perform(patch(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"consents\":{\"THIRD_PARTY_POLICY\":true}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(initial.path("id").asText()))
        .andExpect(jsonPath("$.createdAt").value(initial.path("createdAt").asText()))
        .andExpect(jsonPath("$.consents.THIRD_PARTY_POLICY").value(true))
        .andExpect(jsonPath("$.consents.HEALTH_INFORMATION").value(false))
        .andReturn();

    var result = mvc.perform(get(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization))
        .andExpect(status().isOk()).andReturn();
    assertThat(mapper.readTree(result.getResponse().getContentAsString()))
        .isEqualTo(mapper.readTree(updated.getResponse().getContentAsString()));
    assertThat(histories.findByUserIdOrderByIdDesc(user.getId())).isEmpty();
  }

  @Test
  void getAndPatchBeforeInitialPostReturn404() throws Exception {
    mvc.perform(get(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CONSENT_NOT_FOUND"));
    mvc.perform(patch(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization)
            .contentType(MediaType.APPLICATION_JSON).content("{\"consents\":{\"HEALTH_INFORMATION\":false}}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CONSENT_NOT_FOUND"));
    assertThat(consents.findByUserId(user.getId())).isEmpty();
  }

  @Test
  void allConsentEndpointsRequireAnExistingAuthenticatedUser() throws Exception {
    mvc.perform(get(ENDPOINT)).andExpect(status().isUnauthorized());
    mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body(false).toString()))
        .andExpect(status().isUnauthorized());
    mvc.perform(patch(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
    users.deleteById(user.getId());
    mvc.perform(get(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void ignoresBodyOrQueryUserIdAndOnlyUpdatesJwtOwner() throws Exception {
    User other = users.saveAndFlush(User.builder().provider(User.Provider.KAKAO)
        .providerId(UUID.randomUUID().toString()).name("Other").build());
    var request = body(false).put("userId", other.getId().toString());
    mvc.perform(post(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization)
            .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
        .andExpect(status().isCreated());
    mvc.perform(patch(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":\"" + other.getId() + "\",\"consents\":{\"HEALTH_INFORMATION\":true}}"))
        .andExpect(status().isOk());
    mvc.perform(get(ENDPOINT).param("userId", other.getId().toString())
            .header(HttpHeaders.AUTHORIZATION, authorization))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.consents.HEALTH_INFORMATION").value(true));
    assertThat(consents.findByUserId(other.getId())).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "null", "{}", "{\"consents\":null}", "{\"consents\":{}}",
      "{\"consents\":{\"HEALTH_INFORMATION\":null}}",
      "{\"consents\":{\"WRONG\":true}}",
      "{\"consents\":{\"HEALTH_INFORMATION\":\"not-a-boolean\"}}"
  })
  void rejectsMalformedCreateAndPatchBodies(String body) throws Exception {
    mvc.perform(post(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization)
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    mvc.perform(patch(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization)
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  @Test
  void postRejectsMissingOrNullItemEvenWhenOtherSixArePresent() throws Exception {
    var missing = body(true);
    ((ObjectNode) missing.path("consents")).remove("AGE_14_PLUS");
    mvc.perform(post(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization)
            .contentType(MediaType.APPLICATION_JSON).content(missing.toString()))
        .andExpect(status().isBadRequest());
    ((ObjectNode) missing.path("consents")).putNull("AGE_14_PLUS");
    mvc.perform(post(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization)
            .contentType(MediaType.APPLICATION_JSON).content(missing.toString()))
        .andExpect(status().isBadRequest());
    assertThat(consents.findByUserId(user.getId())).isEmpty();
  }

  @Test
  void kakaoAutoSignupAndExistingLoginAreIndependentOfConsentStorage() throws Exception {
    long providerId = PROVIDERS.incrementAndGet();
    kakaoUser(providerId);
    var response = login().andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isString())
        .andExpect(jsonPath("$.signupToken").doesNotExist()).andReturn();
    User created = users.findByProviderAndProviderId(User.Provider.KAKAO, Long.toString(providerId))
        .orElseThrow();
    assertThat(consents.findByUserId(created.getId())).isEmpty();
    assertThat(histories.findByUserIdOrderByIdDesc(created.getId())).isEmpty();
    login().andExpect(status().isOk());
    String token = mapper.readTree(response.getResponse().getContentAsString()).path("accessToken").asText();
    mvc.perform(post(ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(body(false).toString()))
        .andExpect(status().isCreated());
  }

  @Test
  void concurrentKakaoLoginsCreateOneAccountWithoutSignupTokens() throws Exception {
    long providerId = PROVIDERS.incrementAndGet();
    kakaoUser(providerId);
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var futures = List.of(1, 2).stream().map(i -> executor.submit(() -> {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return login().andReturn().getResponse();
      })).toList();
      start.countDown();
      for (var future : futures) {
        assertThat(future.get(10, TimeUnit.SECONDS).getStatus()).isEqualTo(200);
      }
      assertThat(users.findByProviderAndProviderId(User.Provider.KAKAO, Long.toString(providerId))).isPresent();
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void swaggerDocumentsNewPathsBodiesAndErrorsAndRemovesOldSignupContract() throws Exception {
    var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
    var docs = mapper.readTree(result.getResponse().getContentAsString());
    var paths = docs.path("paths");
    var endpoint = paths.path(ENDPOINT);
    assertThat(endpoint.has("get")).isTrue();
    assertThat(endpoint.has("post")).isTrue();
    assertThat(endpoint.has("patch")).isTrue();
    assertThat(endpoint.has("delete")).isFalse();
    assertThat(endpoint.path("post").path("responses").has("201")).isTrue();
    assertThat(endpoint.path("post").path("responses").has("409")).isTrue();
    assertThat(endpoint.path("get").path("responses").has("404")).isTrue();
    assertThat(endpoint.path("get").path("responses").has("401")).isTrue();
    assertThat(docs.path("components").path("schemas").path("ConsentResponse")
        .path("properties").has("consents")).isTrue();
    assertThat(paths.has("/api/auth/kakao/signup")).isFalse();
    assertThat(paths.has("/api/v1/users/me/consents")).isFalse();
  }

  private void kakaoUser(long providerId) {
    when(kakao.requestToken(anyString())).thenReturn(
        new KakaoTokenResponse("bearer", "test-kakao-access", null, 3600L, null));
    when(kakao.requestUserInfo("test-kakao-access")).thenReturn(new KakaoUserInfoResponse(providerId,
        new KakaoUserInfoResponse.KakaoAccount("test@example.test",
            new KakaoUserInfoResponse.Profile("Kakao", null))));
    when(refresh.issue(any())).thenAnswer(invocation -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      return "test-refresh";
    });
  }

  private org.springframework.test.web.servlet.ResultActions login() throws Exception {
    return mvc.perform(post("/api/auth/kakao/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"authorizationCode\":\"test-code\"}"));
  }

  private ObjectNode body(boolean agreed) {
    var body = mapper.createObjectNode();
    var values = body.putObject("consents");
    for (var type : ConsentType.values()) {
      values.put(type.name(), agreed);
    }
    return body;
  }
}
