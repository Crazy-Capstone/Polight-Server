package polight.server.domain.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import polight.server.domain.chat.client.RagQueryClient;
import polight.server.domain.chat.dto.CertificateStatus;
import polight.server.domain.chat.dto.ClaimDocument;
import polight.server.domain.chat.dto.RagQueryRequest;
import polight.server.domain.chat.dto.RagQueryResponse;
import polight.server.domain.chat.repository.ChatMessageRepository;
import polight.server.domain.chat.repository.ChatSessionRepository;
import polight.server.domain.insurance.entity.DocumentKind;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.insurance.repository.PolicyDocumentRepository;
import polight.server.domain.terms.service.TermsChunkQueryService;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.trip.repository.TripRepository;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.repository.UserRepository;
import polight.server.domain.user.repository.UserWithdrawalRepository;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;
import polight.server.global.security.JwtTokenProvider;

@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=${POLIGHT_CHAT_TEST_DB_URL:jdbc:h2:mem:unregistered-chat;MODE=PostgreSQL;NON_KEYWORDS=VALUE}",
    "spring.datasource.driver-class-name=${POLIGHT_CHAT_TEST_DB_DRIVER:org.h2.Driver}",
    "spring.datasource.username=${POLIGHT_CHAT_TEST_DB_USER:sa}",
    "spring.datasource.password=",
    "spring.flyway.enabled=${POLIGHT_CHAT_TEST_FLYWAY:false}",
    "spring.jpa.hibernate.ddl-auto=${POLIGHT_CHAT_TEST_DDL:create-drop}",
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
class UnregisteredChatHttpTest {

  private static final String ENDPOINT = "/api/v1/chat/unregistered/messages";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private UserRepository users;
  @Autowired private TripRepository trips;
  @Autowired private PolicyDocumentRepository documents;
  @Autowired private ChatSessionRepository sessions;
  @Autowired private ChatMessageRepository messages;
  @Autowired private JwtTokenProvider tokens;
  @Autowired private UserWithdrawalRepository withdrawal;
  @Autowired private PlatformTransactionManager transactions;
  @MockitoBean private RagQueryClient ai;
  @MockitoBean private TermsChunkQueryService chunks;

  private User user;
  private String authorization;

  @BeforeEach
  void setUp() {
    user = newUser();
    authorization = "Bearer " + tokens.createAccessToken(user);
    when(ai.query(any())).thenAnswer(invocation -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      return new RagQueryResponse("General guidance", "TEXT", List.of("POLICE"), List.of(),
          List.of(new ClaimDocument("Receipt", "UNKNOWN", null, "General", null, null, null, null, null)),
          "Check official guidance", "https://example.com/help");
    });
  }

  @Test
  void emptyHistoryDoesNotCreateSession() throws Exception {
    mvc.perform(get(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization))
        .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").isEmpty())
        .andExpect(jsonPath("$.messages").isEmpty());
    assertThat(sessions.findByUserIdAndTripIsNull(user.getId())).isEmpty();
    verifyNoInteractions(ai);
  }

  @Test
  void storesUserScopedConversationAndRestoresMetadataWithoutTrip() throws Exception {
    var answer = ask("  Help  ");
    var sessionId = UUID.fromString(answer.path("sessionId").asText());
    var session = sessions.findById(sessionId).orElseThrow();
    assertThat(session.getTrip()).isNull();
    assertThat(session.getUser().getId()).isEqualTo(user.getId());
    var captor = ArgumentCaptor.forClass(RagQueryRequest.class);
    verify(ai).query(captor.capture());
    var request = captor.getValue();
    assertThat(request.userId()).isEqualTo(user.getId());
    assertThat(request.tripId()).isNull();
    assertThat(request.termsId()).isNull();
    assertThat(request.documentId()).isNull();
    assertThat(request.policyId()).isNull();
    assertThat(request.certificateStatus()).isEqualTo(CertificateStatus.UNREGISTERED);
    assertThat(request.coverages()).isEmpty();
    assertThat(request.coveragesComplete()).isFalse();
    assertThat(request.history()).isEmpty();
    assertThat(request.question()).isEqualTo("Help");
    var serialized = mapper.readTree(mapper.writeValueAsString(request));
    assertThat(serialized.path("certificateStatus").asText()).isEqualTo("UNREGISTERED");
    assertThat(serialized.path("tripId").isNull()).isTrue();

    var history = history();
    assertThat(history.path("messages")).hasSize(2);
    assertThat(history.path("messages").get(0).path("content").asText()).isEqualTo("Help");
    var restored = history.path("messages").get(1);
    var stored = mapper.readTree(messages.findById(UUID.fromString(answer.path("messageId").asText()))
        .orElseThrow().getMetadataJson());
    for (String field : List.of("sources", "suggestedContacts", "claimDocuments", "claimDocumentsNotice", "claimDocumentsUrl")) {
      assertThat(restored.path(field)).isEqualTo(answer.path(field));
      assertThat(stored.path(field)).isEqualTo(answer.path(field));
    }
    assertThat(restored.path("content")).isEqualTo(answer.path("answer"));
    assertThat(ask("Next question").path("sessionId")).isEqualTo(answer.path("sessionId"));
    assertThat(history().path("messages")).hasSize(4);
    verifyNoInteractions(chunks);
  }

  @Test
  void latestHistoryIsLimitedToTenMessagesAndGetLimitIsClamped() throws Exception {
    for (int i = 0; i < 7; i++) {
      ask("Question " + i);
    }
    var captor = ArgumentCaptor.forClass(RagQueryRequest.class);
    verify(ai, org.mockito.Mockito.times(7)).query(captor.capture());
    assertThat(captor.getValue().history()).hasSize(10);
    assertThat(history().path("messages")).hasSize(14);
    mvc.perform(get(ENDPOINT).param("limit", "0").header(HttpHeaders.AUTHORIZATION, authorization))
        .andExpect(status().isOk()).andExpect(jsonPath("$.messages.length()").value(1));
    mvc.perform(get(ENDPOINT).param("limit", "5000").header(HttpHeaders.AUTHORIZATION, authorization))
        .andExpect(status().isOk()).andExpect(jsonPath("$.messages.length()").value(14));
  }

  @Test
  void requiresAuthenticationForBothEndpointsAndRejectsDeletedUser() throws Exception {
    mvc.perform(get(ENDPOINT)).andExpect(status().isUnauthorized());
    mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Help\"}"))
        .andExpect(status().isUnauthorized());
    users.deleteById(user.getId());
    mvc.perform(get(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization)).andExpect(status().isUnauthorized());
    verifyNoInteractions(ai);
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "{}", "{\"question\":null}", "{\"question\":\"  \"}"})
  void rejectsInvalidQuestionBeforeCreatingSession(String body) throws Exception {
    mvc.perform(post(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization)
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    assertThat(sessions.findByUserIdAndTripIsNull(user.getId())).isEmpty();
    verifyNoInteractions(ai);
  }

  @Test
  void rejectsOverlongQuestion() throws Exception {
    mvc.perform(post(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(java.util.Map.of("question", "x".repeat(2001)))))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(ai);
  }

  @Test
  void expiredTripStillBlocksUnregisteredPostAndGetBeforeWriting() throws Exception {
    expiredTrip(user);
    mvc.perform(post(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization)
            .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Help\"}"))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CHAT_TRIP_ALREADY_REGISTERED"));
    mvc.perform(get(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CHAT_TRIP_ALREADY_REGISTERED"));
    assertThat(sessions.findByUserIdAndTripIsNull(user.getId())).isEmpty();
    assertThat(messages.findBySessionUserIdOrderByCreatedAtDesc(user.getId())).isEmpty();
    verifyNoInteractions(ai);
  }

  @Test
  void cannotChooseAnotherUserOrTripThroughBodyOrQuery() throws Exception {
    User other = newUser();
    Trip otherTrip = expiredTrip(other);
    var body = mapper.createObjectNode().put("question", "Help").put("userId", other.getId().toString())
        .put("tripId", otherTrip.getId().toString()).put("certificateStatus", "COMPLETED");
    mvc.perform(post(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization)
            .contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isOk());
    var captor = ArgumentCaptor.forClass(RagQueryRequest.class);
    verify(ai).query(captor.capture());
    assertThat(captor.getValue().userId()).isEqualTo(user.getId());
    assertThat(captor.getValue().tripId()).isNull();
    assertThat(captor.getValue().certificateStatus()).isEqualTo(CertificateStatus.UNREGISTERED);
    mvc.perform(get(ENDPOINT).param("userId", other.getId().toString())
            .header(HttpHeaders.AUTHORIZATION, authorization))
        .andExpect(status().isOk()).andExpect(jsonPath("$.messages.length()").value(2));
    assertThat(sessions.findByUserIdAndTripIsNull(other.getId())).isEmpty();
    User third = newUser();
    mvc.perform(get(ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.createAccessToken(third)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.messages").isEmpty());
  }

  @Test
  void registrationSwitchesToExistingTripChatAndPreservesEarlierConversation() throws Exception {
    var old = ask("Before registration");
    Trip trip = expiredTrip(user);
    documents.saveAndFlush(PolicyDocument.builder().user(user).trip(trip)
        .documentKind(DocumentKind.CERTIFICATE).originalFilename("certificate.pdf")
        .storedFilePath("test/" + UUID.randomUUID() + ".pdf").build());
    mvc.perform(get(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization)).andExpect(status().isConflict());
    mvc.perform(post(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization)
            .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"After registration\"}"))
        .andExpect(status().isConflict());
    var result = mvc.perform(post("/api/v1/trips/" + trip.getId() + "/chat/messages")
            .header(HttpHeaders.AUTHORIZATION, authorization).contentType(MediaType.APPLICATION_JSON)
            .content("{\"question\":\"After registration\"}"))
        .andExpect(status().isOk()).andReturn();
    assertThat(mapper.readTree(result.getResponse().getContentAsString()).path("sessionId"))
        .isNotEqualTo(old.path("sessionId"));
    assertThat(sessions.findByUserIdAndTripIsNull(user.getId()).orElseThrow().getId().toString())
        .isEqualTo(old.path("sessionId").asText());
    assertThat(messages.findById(UUID.fromString(old.path("messageId").asText()))).isPresent();
    var captor = ArgumentCaptor.forClass(RagQueryRequest.class);
    verify(ai, org.mockito.Mockito.times(2)).query(captor.capture());
    assertThat(captor.getValue().tripId()).isEqualTo(trip.getId());
    assertThat(captor.getValue().certificateStatus()).isEqualTo(CertificateStatus.ANALYZING);
  }

  @Test
  void concurrentFirstQuestionsReuseOneUnregisteredSession() throws Exception {
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var futures = List.of(1, 2).stream().map(i -> executor.submit(() -> {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return ask("Concurrent " + i);
      })).toList();
      start.countDown();
      var first = futures.get(0).get(10, TimeUnit.SECONDS);
      var second = futures.get(1).get(10, TimeUnit.SECONDS);
      assertThat(first.path("sessionId")).isEqualTo(second.path("sessionId"));
      assertThat(history().path("messages")).hasSize(4);
      assertThat(sessions.findByUserIdAndTripIsNull(user.getId())).isPresent();
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void aiFailureReturns502ButKeepsQuestion() throws Exception {
    when(ai.query(any())).thenThrow(new BaseException(ErrorCode.AI_CHAT_REQUEST_FAILED));
    mvc.perform(post(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization)
            .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Keep this question\"}"))
        .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("AI_CHAT_REQUEST_FAILED"));
    assertThat(history().path("messages")).hasSize(1);
    assertThat(history().path("messages").get(0).path("content").asText()).isEqualTo("Keep this question");
  }

  @Test
  void withdrawalAlsoDeletesTriplessChatWithoutAffectingOtherUsers() throws Exception {
    var answer = ask("Before withdrawal");
    User other = newUser();
    new TransactionTemplate(transactions).executeWithoutResult(status -> withdrawal.deleteUserData(user.getId()));

    assertThat(users.findById(user.getId())).isEmpty();
    assertThat(sessions.findById(UUID.fromString(answer.path("sessionId").asText()))).isEmpty();
    assertThat(messages.findById(UUID.fromString(answer.path("messageId").asText()))).isEmpty();
    assertThat(users.findById(other.getId())).isPresent();
  }

  @Test
  void swaggerIncludesBothUnregisteredEndpointsAndExistingContract() throws Exception {
    var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
    var paths = mapper.readTree(result.getResponse().getContentAsString()).path("paths");
    var post = paths.path(ENDPOINT).path("post");
    assertThat(post.path("responses").has("200")).isTrue();
    assertThat(post.path("responses").has("409")).isTrue();
    assertThat(post.path("responses").has("503")).isFalse();
    assertThat(post.path("responses").has("502")).isTrue();
    assertThat(post.path("description").asText()).contains("tripId=null", "미확정", "별도 활성화 설정 없이");
    assertThat(paths.path(ENDPOINT).has("get")).isTrue();
    assertThat(paths.path("/api/v1/trips/{tripId}/chat/messages").has("post")).isTrue();
  }

  private JsonNode ask(String question) throws Exception {
    var result = mvc.perform(post(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(java.util.Map.of("question", question))))
        .andExpect(status().isOk()).andReturn();
    return mapper.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode history() throws Exception {
    var result = mvc.perform(get(ENDPOINT).header(HttpHeaders.AUTHORIZATION, authorization))
        .andExpect(status().isOk()).andReturn();
    return mapper.readTree(result.getResponse().getContentAsString());
  }

  private User newUser() {
    return users.saveAndFlush(User.builder().provider(User.Provider.KAKAO)
        .providerId(UUID.randomUUID().toString()).name("Tester").build());
  }

  private Trip expiredTrip(User owner) {
    return trips.saveAndFlush(Trip.builder().user(owner).name("Expired trip")
        .startDate(LocalDate.now().minusDays(10)).endDate(LocalDate.now().minusDays(5)).build());
  }
}
