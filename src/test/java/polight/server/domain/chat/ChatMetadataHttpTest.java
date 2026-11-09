package polight.server.domain.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.analysis.repository.AnalysisResultRepository;
import polight.server.domain.chat.repository.ChatMessageRepository;
import polight.server.domain.chat.service.ChatMessageService;
import polight.server.domain.insurance.entity.DocumentKind;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.insurance.repository.PolicyDocumentRepository;
import polight.server.domain.terms.entity.PolicyTerms;
import polight.server.domain.terms.repository.PolicyTermsRepository;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.trip.repository.TripRepository;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.repository.UserRepository;
import polight.server.global.security.JwtTokenProvider;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:chat-metadata;MODE=PostgreSQL;NON_KEYWORDS=VALUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "spring.docker.compose.enabled=false",
    "analysis.timeout.enabled=false",
    "storage.type=local",
    "terms.matching.provider=local",
    "oauth.kakao.client-id=test-client-id",
    "oauth.kakao.redirect-uri=http://localhost/test",
    "security.jwt.secret=test-secret-key-with-at-least-32-bytes"
})
@DirtiesContext
class ChatMetadataHttpTest {

  private static final AtomicReference<String> AI_RESPONSE = new AtomicReference<>();
  private static final AtomicInteger AI_CALLS = new AtomicInteger();
  private static HttpServer aiServer;

  @Autowired private TestRestTemplate http;
  @Autowired private ObjectMapper mapper;
  @Autowired private UserRepository users;
  @Autowired private TripRepository trips;
  @Autowired private PolicyDocumentRepository documents;
  @Autowired private PolicyTermsRepository terms;
  @Autowired private AnalysisResultRepository analyses;
  @Autowired private ChatMessageRepository messages;
  @Autowired private ChatMessageService messageService;
  @Autowired private JwtTokenProvider tokens;

  private User owner;
  private Trip trip;

  @DynamicPropertySource
  static void registerAiServer(DynamicPropertyRegistry registry) {
    try {
      aiServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
    aiServer.createContext("/internal/rag/query", exchange -> {
      try (exchange) {
        AI_CALLS.incrementAndGet();
        byte[] payload = AI_RESPONSE.get().getBytes(StandardCharsets.UTF_8);
        exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, payload.length);
        exchange.getResponseBody().write(payload);
      }
    });
    aiServer.start();
    registry.add("ai.server.base-url", () -> "http://127.0.0.1:" + aiServer.getAddress().getPort());
  }

  @AfterAll
  static void stopAiServer() {
    if (aiServer != null) {
      aiServer.stop(0);
    }
  }

  @BeforeEach
  void createTrip() {
    AI_CALLS.set(0);
    AI_RESPONSE.set("{\"answer\":\"Test answer\",\"responseType\":\"TEXT\"}");
    owner = users.save(User.builder().provider(User.Provider.KAKAO)
        .providerId(UUID.randomUUID().toString()).name("tester").build());
    trip = trips.save(Trip.builder().user(owner).name("Trip")
        .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(1)).build());
  }

  @Test
  void keepsContactsSourcesAndAllClaimEvidenceInAnswerStorageAndHistory() throws Exception {
    linkTerms();
    AI_RESPONSE.set("""
        {
          "answer": "Submit these documents.",
          "responseType": "TEXT",
          "suggestedContacts": ["POLICE", "AMBULANCE"],
          "sources": [{"page": 20, "quote": "Original policy quote"}],
          "claimDocuments": [
            {
              "documentName": "Medical receipt",
              "requirementType": "REQUIRED",
              "conditionText": null,
              "coverageTitle": "Overseas medical expenses",
              "sourceChunkId": "11111111-1111-1111-1111-111111111111",
              "sourceQuote": "The receipt is required.",
              "sectionTitle": "Claim documents",
              "pageStart": 20,
              "pageEnd": 21
            },
            {
              "documentName": "Police report",
              "requirementType": "CONDITIONAL",
              "conditionText": "In the event of theft",
              "coverageTitle": null,
              "sourceChunkId": null,
              "sourceQuote": null,
              "sectionTitle": null,
              "pageStart": null,
              "pageEnd": null
            },
            {
              "documentName": "Other supporting evidence",
              "requirementType": "UNKNOWN",
              "conditionText": null,
              "coverageTitle": null,
              "sourceChunkId": null,
              "sourceQuote": null,
              "sectionTitle": null,
              "pageStart": null,
              "pageEnd": null,
              "futureField": "ignored"
            }
          ]
        }
        """);

    JsonNode answer = ask();
    JsonNode expected = mapper.readTree(AI_RESPONSE.get());
    ((ObjectNode) expected.path("claimDocuments").get(2)).remove("futureField");
    assertThat(answer.path("responseType").asText()).isEqualTo("TEXT");
    assertThat(answer.path("suggestedContacts")).isEqualTo(expected.path("suggestedContacts"));
    assertThat(answer.path("claimDocuments")).isEqualTo(expected.path("claimDocuments"));
    assertThat(answer.path("sources").get(0).path("quote").asText()).isEqualTo("Original policy quote");

    JsonNode stored = storedMetadata(answer);
    JsonNode history = historyMessage(answer.path("messageId").asText());
    for (String field : new String[] {"sources", "suggestedContacts", "claimDocuments"}) {
      assertThat(stored.path(field)).isEqualTo(answer.path(field));
      assertThat(history.path(field)).isEqualTo(answer.path(field));
    }
    assertThat(stored.path("latencyMs").asLong(-1)).isNotNegative();
    assertThat(AI_CALLS).hasValue(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "",
      ", \"suggestedContacts\": null, \"claimDocuments\": null",
      ", \"suggestedContacts\": [], \"claimDocuments\": []"
  })
  void normalizesOmittedNullAndEmptyLists(String fields) throws Exception {
    linkTerms();
    AI_RESPONSE.set("{\"answer\":\"No checklist\",\"responseType\":\"TEXT\"" + fields + "}");

    JsonNode answer = ask();
    assertEmptyLists(answer);
    assertEmptyLists(storedMetadata(answer));
    assertEmptyLists(historyMessage(answer.path("messageId").asText()));
    assertThat(AI_CALLS).hasValue(1);
  }

  @Test
  void restoresLegacyMetadataAndSkipsAiWhenNoTerms() {
    JsonNode answer = ask();
    assertThat(answer.path("answer").asText()).isNotBlank().isNotEqualTo("Test answer");
    assertEmptyLists(answer);
    JsonNode savedAnswer = historyMessage(answer.path("messageId").asText());
    assertThat(savedAnswer.path("content")).isEqualTo(answer.path("answer"));
    assertEmptyLists(savedAnswer);
    assertThat(AI_CALLS).hasValue(0);

    UUID sessionId = UUID.fromString(answer.path("sessionId").asText());
    var oldWithContacts = messageService.appendAssistantMessage(sessionId, "Old with contacts",
        """
        {"sources":[{"quote":"Old quote"}],"suggestedContacts":["EMBASSY"],"latencyMs":35}
        """);
    var oldWithoutContacts = messageService.appendAssistantMessage(sessionId, "Old without contacts",
        """
        {"sources":[],"latencyMs":23}
        """);

    JsonNode legacy = historyMessage(oldWithContacts.getId().toString());
    assertThat(legacy.path("suggestedContacts").get(0).asText()).isEqualTo("EMBASSY");
    assertThat(legacy.path("sources").get(0).path("quote").asText()).isEqualTo("Old quote");
    assertThat(legacy.path("claimDocuments")).isEqualTo(mapper.createArrayNode());
    assertEmptyLists(historyMessage(oldWithoutContacts.getId().toString()));
  }

  @Test
  void documentsClaimDocumentsInBothSwaggerResponses() {
    JsonNode schemas = http.getForObject("/v3/api-docs", JsonNode.class)
        .path("components").path("schemas");
    for (String schema : new String[] {"ChatAnswerResponse", "Message"}) {
      JsonNode claimDocuments = schemas.path(schema).path("properties").path("claimDocuments");
      assertThat(claimDocuments.path("items").path("$ref").asText())
          .isEqualTo("#/components/schemas/ClaimDocument");
    }
  }

  private void linkTerms() {
    var policy = terms.save(PolicyTerms.official("Test insurer", "Travel policy", null, null));
    var certificate = documents.save(PolicyDocument.builder().user(owner).trip(trip)
        .documentKind(DocumentKind.CERTIFICATE).originalFilename("certificate.pdf")
        .storedFilePath("test/" + trip.getId() + ".pdf").build());
    var analysis = AnalysisResult.builder().document(certificate).build();
    analysis.markCompleted(LocalDateTime.now());
    analysis.linkTerms(policy, "EXACT", null);
    analyses.save(analysis);
  }

  private JsonNode ask() {
    var response = http.exchange(endpoint(), HttpMethod.POST,
        new HttpEntity<>("{\"question\":\"What do I need?\"}", headers()), JsonNode.class);
    assertThat(response.getStatusCode()).as("body: %s", response.getBody()).isEqualTo(HttpStatus.OK);
    return response.getBody();
  }

  private JsonNode storedMetadata(JsonNode answer) throws IOException {
    var message = messages.findById(UUID.fromString(answer.path("messageId").asText())).orElseThrow();
    return mapper.readTree(message.getMetadataJson());
  }

  private JsonNode historyMessage(String messageId) {
    var response = http.exchange(endpoint(), HttpMethod.GET,
        new HttpEntity<>(headers()), JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return StreamSupport.stream(response.getBody().path("messages").spliterator(), false)
        .filter(message -> messageId.equals(message.path("messageId").asText()))
        .findFirst().orElseThrow();
  }

  private void assertEmptyLists(JsonNode json) {
    assertThat(json.path("suggestedContacts")).isEqualTo(mapper.createArrayNode());
    assertThat(json.path("claimDocuments")).isEqualTo(mapper.createArrayNode());
  }

  private String endpoint() {
    return "/api/v1/trips/" + trip.getId() + "/chat/messages";
  }

  private HttpHeaders headers() {
    var headers = new HttpHeaders();
    headers.setBearerAuth(tokens.createAccessToken(owner));
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }
}
