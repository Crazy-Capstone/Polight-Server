package polight.server.domain.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.analysis.repository.AnalysisResultRepository;
import polight.server.domain.auth.service.RefreshTokenStore;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.insurance.repository.PolicyDocumentRepository;
import polight.server.domain.insurance.storage.PolicyDocumentStorage;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.trip.repository.TripDeletionRepository;
import polight.server.domain.trip.repository.TripRepository;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.repository.UserRepository;
import polight.server.global.security.JwtTokenProvider;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:trip-delete-http;MODE=PostgreSQL;NON_KEYWORDS=VALUE",
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
    "security.jwt.secret=test-secret-key-with-at-least-32-bytes",
    "internal.api-key=local-trip-deletion-test"
})
@DirtiesContext
class TripDeletionHttpTest {

  @TempDir static Path directory;
  @Autowired private TestRestTemplate http;
  @Autowired private UserRepository users;
  @Autowired private TripRepository trips;
  @Autowired private PolicyDocumentRepository documents;
  @Autowired private AnalysisResultRepository analyses;
  @Autowired private JwtTokenProvider tokens;
  @MockitoSpyBean private TripDeletionRepository deletionRepository;
  @MockitoSpyBean private PolicyDocumentStorage storage;
  @MockitoBean private RefreshTokenStore refreshTokenStore;
  private User owner;
  private String token;

  @DynamicPropertySource
  static void configureStorage(DynamicPropertyRegistry registry) {
    registry.add("storage.policy-documents-directory", () -> directory.toString());
  }

  @BeforeEach
  void createUser() {
    owner = user();
    token = tokens.createAccessToken(owner);
  }

  @Test
  void deletesOwnTripAndFileAndKeepsAccountTokenAndOtherTrips() {
    Fixture target = fixture(owner);
    Fixture ownOther = fixture(owner);
    Fixture foreign = fixture(user());

    var response = delete(target.trip().getId().toString(), true);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(response.getBody()).isNull();
    assertThat(trips.findById(target.trip().getId())).isEmpty();
    assertThat(documents.findById(target.document().getId())).isEmpty();
    assertThat(analyses.findById(target.analysis().getId())).isEmpty();
    assertThat(target.file()).doesNotExist();
    assertPreserved(ownOther);
    assertPreserved(foreign);
    assertThat(users.findById(owner.getId())).isPresent();
    assertThat(http.exchange("/api/v1/trips", HttpMethod.GET, authenticated(), JsonNode.class)
        .getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(delete(target.trip().getId().toString(), true).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    verifyNoInteractions(refreshTokenStore);
  }

  @Test
  void foreignTripReturns404WithoutDeletingAnything() {
    Fixture target = fixture(user());
    var response = delete(target.trip().getId().toString(), true);
    assertError(response, HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND");
    assertPreserved(target);
    verify(storage, never()).delete(anyString());
  }

  @Test
  void missingTripReturns404() {
    assertError(delete(UUID.randomUUID().toString(), true), HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND");
    verify(storage, never()).delete(anyString());
  }

  @Test
  void invalidUuidReturns400() {
    assertError(delete("invalid-uuid", true), HttpStatus.BAD_REQUEST, "INVALID_INPUT");
    verify(storage, never()).delete(anyString());
  }

  @Test
  void missingAuthenticationReturns401() {
    Fixture target = fixture(owner);
    assertError(delete(target.trip().getId().toString(), false), HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED");
    assertPreserved(target);
    verify(storage, never()).delete(anyString());
  }

  @Test
  void databaseFailureReturns500AndRollsBackBeforeFileDeletion() {
    Fixture target = fixture(owner);
    doAnswer(invocation -> {
      invocation.callRealMethod();
      throw new IllegalStateException("Forced database failure");
    }).when(deletionRepository).deleteTripData(any(), any());

    var response = delete(target.trip().getId().toString(), true);

    assertError(response, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
    assertPreserved(target);
    verify(storage, never()).delete(anyString());
  }

  @Test
  void partialFileFailureStillReturns204AndDeletesRemainingFiles() {
    Fixture target = fixture(owner);
    String secondPath = storage.store(new MockMultipartFile("file", "second.pdf", "application/pdf", new byte[] {2}));
    documents.save(PolicyDocument.builder().user(owner).trip(target.trip())
        .originalFilename("second.pdf").storedFilePath(secondPath).build());
    doThrow(new IllegalStateException("S3 unavailable")).when(storage).delete(target.file().toString());

    var response = delete(target.trip().getId().toString(), true);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(trips.findById(target.trip().getId())).isEmpty();
    assertThat(target.file()).exists();
    assertThat(Path.of(secondPath)).doesNotExist();
    verify(storage).delete(target.file().toString());
    verify(storage).delete(secondPath);
  }

  @Test
  void lateAiCallbacksReturn404WithoutRecreatingRecords() {
    Fixture target = fixture(owner);
    assertThat(delete(target.trip().getId().toString(), true).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Internal-Api-Key", "local-trip-deletion-test");
    for (String action : Set.of("complete", "fail")) {
      var response = http.exchange("/internal/analysis-results/" + target.analysis().getId() + "/" + action,
          HttpMethod.POST, new HttpEntity<>("{\"summary\":\"late\",\"errorMessage\":\"late\",\"coverageItems\":[]}", headers), JsonNode.class);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(response.getBody().path("code").asText()).isEqualTo("ANALYSIS_RESULT_NOT_FOUND");
    }
    assertThat(documents.findById(target.document().getId())).isEmpty();
    assertThat(analyses.findById(target.analysis().getId())).isEmpty();
  }

  @Test
  void swaggerDocumentsNoBodyAndAllResponseCodesAndFileDeletionLimit() {
    JsonNode api = http.getForObject("/v3/api-docs", JsonNode.class);
    JsonNode operation = api.path("paths").path("/api/v1/trips/{tripId}").path("delete");
    assertThat(operation.isMissingNode()).isFalse();
    assertThat(operation.has("requestBody")).isFalse();
    var responses = new java.util.HashSet<String>();
    operation.path("responses").fieldNames().forEachRemaining(responses::add);
    assertThat(responses).contains("204", "400", "401", "404", "500");
    assertThat(operation.path("responses").path("204").has("content")).isFalse();
    assertThat(operation.path("description").asText()).contains("커밋", "보장하지", "자동 재시도", "수동", "유실");
    assertThat(StreamSupport.stream(operation.path("parameters").spliterator(), false)
        .map(node -> node.path("name").asText()).collect(Collectors.toSet())).contains("tripId");
  }

  private User user() {
    return users.save(User.builder().provider(User.Provider.KAKAO)
        .providerId(UUID.randomUUID().toString()).name("tester").build());
  }

  private Fixture fixture(User user) {
    Trip trip = trips.save(Trip.builder().user(user).name("Trip")
        .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(1)).build());
    String path = storage.store(new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[] {1}));
    PolicyDocument document = documents.save(PolicyDocument.builder().user(user).trip(trip)
        .originalFilename("test.pdf").storedFilePath(path).build());
    AnalysisResult analysis = analyses.save(AnalysisResult.builder().document(document).build());
    return new Fixture(trip, document, analysis, Path.of(path));
  }

  private ResponseEntity<JsonNode> delete(String id, boolean authenticated) {
    return http.exchange("/api/v1/trips/" + id, HttpMethod.DELETE,
        authenticated ? authenticated() : HttpEntity.EMPTY, JsonNode.class);
  }

  private HttpEntity<Void> authenticated() {
    var headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return new HttpEntity<>(headers);
  }

  private void assertPreserved(Fixture fixture) {
    assertThat(trips.findById(fixture.trip().getId())).isPresent();
    assertThat(documents.findById(fixture.document().getId())).isPresent();
    assertThat(analyses.findById(fixture.analysis().getId())).isPresent();
    assertThat(fixture.file()).exists();
  }

  private void assertError(ResponseEntity<JsonNode> response, HttpStatus status, String code) {
    assertThat(response.getStatusCode()).isEqualTo(status);
    assertThat(response.getBody().path("code").asText()).isEqualTo(code);
  }

  private record Fixture(Trip trip, PolicyDocument document, AnalysisResult analysis, Path file) {}
}
