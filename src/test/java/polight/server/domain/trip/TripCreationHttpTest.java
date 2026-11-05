package polight.server.domain.trip;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import polight.server.domain.analysis.repository.AnalysisResultRepository;
import polight.server.domain.insurance.repository.PolicyDocumentRepository;
import polight.server.domain.trip.repository.TripRepository;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.repository.UserRepository;
import polight.server.global.security.JwtTokenProvider;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:trip-creation;MODE=PostgreSQL;NON_KEYWORDS=VALUE",
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
class TripCreationHttpTest {

  @TempDir static Path documentDirectory;

  @Autowired private TestRestTemplate http;
  @Autowired private ObjectMapper mapper;
  @Autowired private UserRepository users;
  @Autowired private TripRepository trips;
  @Autowired private PolicyDocumentRepository documents;
  @Autowired private AnalysisResultRepository analyses;
  @Autowired private JwtTokenProvider tokens;
  private String accessToken;

  @DynamicPropertySource
  static void configureStorage(DynamicPropertyRegistry registry) {
    registry.add("storage.policy-documents-directory", () -> documentDirectory.toString());
  }

  @BeforeEach
  void createUser() {
    User user = users.save(User.builder().provider(User.Provider.KAKAO)
        .providerId(UUID.randomUUID().toString()).name("tester").build());
    accessToken = tokens.createAccessToken(user);
  }

  static Stream<Arguments> acceptedCountryLists() {
    return Stream.of(
        Arguments.of("omitted", "omitted", List.of()),
        Arguments.of("null", "null", List.of()),
        Arguments.of("empty", "[]", List.of()),
        Arguments.of("single", "[\"JP\"]", List.of("JP")),
        Arguments.of("multiple", "[\"JP\",\"KR\",\"FR\"]", List.of("JP", "KR", "FR")),
        Arguments.of("duplicates", "[\"FR\",\"JP\",\"FR\",\"KR\"]", List.of("FR", "JP", "KR")));
  }

  @ParameterizedTest(name = "countries: {0}")
  @MethodSource("acceptedCountryLists")
  void createsTripDocumentAndAnalysisWithCountries(
      String name, String countryJson, List<String> expectedCountries) throws Exception {
    ObjectNode request = request();
    if (!countryJson.equals("omitted")) {
      request.set("countries", mapper.readTree(countryJson));
    }

    var response = create(request, new byte[] {1, 2, 3}, true);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode body = response.getBody();
    UUID tripId = UUID.fromString(body.path("trip").path("id").asText());
    UUID documentId = UUID.fromString(body.path("document").path("id").asText());
    UUID analysisId = UUID.fromString(body.path("analysis").path("id").asText());
    var stored = trips.findById(tripId).orElseThrow();
    assertThat(stored.getCountries()).containsExactlyElementsOf(expectedCountries);
    assertThat(body.path("trip").path("countries")).isEqualTo(mapper.valueToTree(expectedCountries));
    assertThat(stored.getName()).isEqualTo("Trip");
    assertThat(stored.getConcerns()).isEmpty();
    assertThat(body.path("trip").path("startDate").asText()).isEqualTo("2026-11-10");
    assertThat(body.path("trip").path("endDate").asText()).isEqualTo("2026-11-20");
    assertThat(body.path("analysis").path("status").asText()).isEqualTo("PROCESSING");
    assertThat(documents.findById(documentId)).isPresent();
    assertThat(analyses.findById(analysisId)).isPresent();
    assertThat(Path.of(documents.findById(documentId).orElseThrow().getStoredFilePath())).exists();
    assertThat(response.getHeaders().getLocation().getPath()).isEqualTo("/api/v1/trips/" + tripId);
  }

  static Stream<String> invalidCountryLists() {
    return Stream.of("[\"ZZ\"]", "[\"jp\"]", "[\"JPN\"]", "[null]", "[\"\"]", "[\"JP\",\"UK\"]");
  }

  @ParameterizedTest
  @MethodSource("invalidCountryLists")
  void invalidCountryDoesNotCreateTripOrDocument(String countryJson) throws Exception {
    ObjectNode request = request();
    request.set("countries", mapper.readTree(countryJson));
    long tripCount = trips.count();
    long documentCount = documents.count();

    var response = create(request, new byte[] {1}, true);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().path("code").asText()).isEqualTo("INVALID_INPUT");
    assertThat(trips.count()).isEqualTo(tripCount);
    assertThat(documents.count()).isEqualTo(documentCount);
  }

  @Test
  void invalidPeriodStillReturnsExistingError() throws Exception {
    ObjectNode request = request().put("endDate", "2026-11-09");
    request.set("countries", mapper.readTree("[\"JP\"]"));
    var response = create(request, new byte[] {1}, true);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().path("code").asText()).isEqualTo("INVALID_TRIP_PERIOD");
  }

  @Test
  void emptyFileStillRollsBackTripCreation() throws Exception {
    long tripCount = trips.count();
    var response = create(request(), new byte[0], true);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().path("code").asText()).isEqualTo("EMPTY_POLICY_DOCUMENT_FILE");
    assertThat(trips.count()).isEqualTo(tripCount);
  }

  @Test
  void authenticationIsStillRequired() throws Exception {
    var response = create(request(), new byte[] {1}, false);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void swaggerDescribesCountriesAndKeepsMultipartCreation() {
    JsonNode api = http.getForObject("/v3/api-docs", JsonNode.class);
    JsonNode schema = api.path("components").path("schemas").path("TripCreateRequest");
    assertThat(schema.path("properties").path("countries").path("type").asText()).isEqualTo("array");
    assertThat(schema.path("properties").path("countries").path("description").asText())
        .contains("ISO", "null");
    assertThat(schema.path("required").toString()).doesNotContain("countries");
    assertThat(api.path("paths").path("/api/v1/trips").path("post")
        .path("requestBody").path("content").has("multipart/form-data")).isTrue();
  }

  private ObjectNode request() {
    ObjectNode request = mapper.createObjectNode().put("name", "Trip")
        .put("startDate", "2026-11-10").put("endDate", "2026-11-20");
    request.putArray("concerns");
    return request;
  }

  private ResponseEntity<JsonNode> create(ObjectNode request, byte[] bytes, boolean authenticated)
      throws Exception {
    var jsonHeaders = new HttpHeaders();
    jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
    var fileHeaders = new HttpHeaders();
    fileHeaders.setContentType(MediaType.APPLICATION_PDF);
    var file = new ByteArrayResource(bytes) {
      @Override
      public String getFilename() {
        return "test.pdf";
      }
    };
    var body = new LinkedMultiValueMap<String, Object>();
    body.add("trip", new HttpEntity<>(mapper.writeValueAsString(request), jsonHeaders));
    body.add("file", new HttpEntity<>(file, fileHeaders));
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    if (authenticated) {
      headers.setBearerAuth(accessToken);
    }
    return http.exchange("/api/v1/trips", HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
  }
}
