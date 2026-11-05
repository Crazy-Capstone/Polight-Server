package polight.server.domain.trip;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.trip.entity.TripStatus;
import polight.server.domain.trip.repository.TripRepository;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.repository.UserRepository;
import polight.server.global.security.JwtTokenProvider;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:trip-update;MODE=PostgreSQL;NON_KEYWORDS=VALUE",
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
class TripUpdateHttpTest {

  @Autowired private TestRestTemplate http;
  @Autowired private ObjectMapper mapper;
  @Autowired private UserRepository users;
  @Autowired private TripRepository trips;
  @Autowired private JwtTokenProvider tokens;
  private User owner;
  private User other;
  private Trip original;
  private String accessToken;

  @BeforeEach
  void createFixture() {
    owner = createUser();
    other = createUser();
    accessToken = tokens.createAccessToken(owner);
    original = trips.save(Trip.builder().user(owner).name("Original")
        .startDate(LocalDate.of(2026, 11, 10)).endDate(LocalDate.of(2026, 11, 20))
        .countries(List.of("FR", "JP")).status(TripStatus.ACTIVE).build());
    original = trips.findById(original.getId()).orElseThrow();
  }

  @Test
  void updatesNameDatesAndCountriesAndImmediatelyReturnsUpdatedResponse() throws Exception {
    ObjectNode request = request().put("name", "  Updated trip  ");
    request.set("countries", mapper.readTree("[\"KR\",\"JP\",\"KR\"]"));

    var response = patch(original.getId(), request, true);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = response.getBody();
    assertThat(body.path("id").asText()).isEqualTo(original.getId().toString());
    assertThat(body.path("name").asText()).isEqualTo("Updated trip");
    assertThat(body.path("startDate").asText()).isEqualTo("2026-11-12");
    assertThat(body.path("endDate").asText()).isEqualTo("2026-11-25");
    assertThat(body.path("countries")).isEqualTo(mapper.valueToTree(List.of("KR", "JP")));
    assertThat(body.path("status").asText()).isEqualTo("ACTIVE");
    assertThat(body.path("concerns")).isEqualTo(mapper.valueToTree(original.getConcerns()));
    assertThat(LocalDateTime.parse(body.path("createdAt").asText())).isEqualTo(original.getCreatedAt());
    assertThat(LocalDateTime.parse(body.path("updatedAt").asText())).isAfterOrEqualTo(original.getUpdatedAt());
    Trip saved = trips.findById(original.getId()).orElseThrow();
    assertThat(saved.getName()).isEqualTo("Updated trip");
    assertThat(saved.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 12));
    assertThat(saved.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 25));
    assertThat(saved.getCountries()).containsExactly("KR", "JP");
    JsonNode detail = get("/api/v1/trips/" + original.getId());
    assertThat(detail.path("name").asText()).isEqualTo("Updated trip");
    assertThat(detail.path("countries")).isEqualTo(body.path("countries"));
    JsonNode list = get("/api/v1/trips");
    assertThat(list.size()).isEqualTo(1);
    assertThat(list.get(0).path("name").asText()).isEqualTo("Updated trip");
    assertThat(list.get(0).path("countries")).isEqualTo(body.path("countries"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"omitted", "null"})
  void omissionOrNullKeepsExistingCountries(String countryJson) throws Exception {
    ObjectNode request = request();
    if (!countryJson.equals("omitted")) {
      request.set("countries", mapper.readTree(countryJson));
    }

    var response = patch(original.getId(), request, true);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().path("name").asText()).isEqualTo("Updated");
    assertThat(response.getBody().path("countries")).isEqualTo(mapper.valueToTree(List.of("FR", "JP")));
    assertThat(trips.findById(original.getId()).orElseThrow().getCountries()).containsExactly("FR", "JP");
  }

  @Test
  void emptyArrayExplicitlyClearsCountries() throws Exception {
    ObjectNode request = request();
    request.putArray("countries");

    var response = patch(original.getId(), request, true);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().path("countries").isArray()).isTrue();
    assertThat(response.getBody().path("countries").size()).isZero();
    assertThat(trips.findById(original.getId()).orElseThrow().getCountries()).isEmpty();
    assertThat(get("/api/v1/trips/" + original.getId()).path("countries").size()).isZero();
  }

  static Stream<String> invalidCountryLists() {
    return Stream.of("[\"ZZ\"]", "[\"jp\"]", "[\"JPN\"]", "[null]", "[\"\"]", "[\"JP\",\"UK\"]");
  }

  @ParameterizedTest
  @MethodSource("invalidCountryLists")
  void invalidCountriesReturn400WithoutChangingTrip(String countryJson) throws Exception {
    ObjectNode request = request();
    request.set("countries", mapper.readTree(countryJson));

    var response = patch(original.getId(), request, true);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().path("code").asText()).isEqualTo("INVALID_INPUT");
    assertUnchanged(original);
  }

  @Test
  void reversedPeriodKeepsExistingErrorAndDoesNotChangeTrip() throws Exception {
    ObjectNode request = request().put("endDate", "2026-11-11");
    request.set("countries", mapper.readTree("[\"KR\"]"));

    var response = patch(original.getId(), request, true);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().path("code").asText()).isEqualTo("INVALID_TRIP_PERIOD");
    assertUnchanged(original);
  }

  @Test
  void sameDayPeriodRemainsAllowed() throws Exception {
    var response = patch(original.getId(), request().put("endDate", "2026-11-12"), true);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().path("startDate").asText()).isEqualTo("2026-11-12");
    assertThat(response.getBody().path("endDate").asText()).isEqualTo("2026-11-12");
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing-name", "blank-name", "long-name", "missing-startDate", "missing-endDate"})
  void existingRequiredFieldRulesArePreserved(String invalidField) throws Exception {
    ObjectNode request = request();
    switch (invalidField) {
      case "missing-name" -> request.remove("name");
      case "blank-name" -> request.put("name", " ");
      case "long-name" -> request.put("name", "x".repeat(101));
      case "missing-startDate" -> request.remove("startDate");
      case "missing-endDate" -> request.remove("endDate");
      default -> throw new IllegalArgumentException(invalidField);
    }
    var response = patch(original.getId(), request, true);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().path("code").asText()).isEqualTo("INVALID_INPUT");
    assertUnchanged(original);
  }

  @Test
  void anotherUsersTripReturns404AndIsNotChanged() throws Exception {
    Trip target = trips.save(Trip.builder().user(other).name("Other trip")
        .startDate(LocalDate.of(2026, 11, 10)).endDate(LocalDate.of(2026, 11, 20))
        .countries(List.of("US")).build());
    target = trips.findById(target.getId()).orElseThrow();
    ObjectNode request = request();
    request.set("countries", mapper.readTree("[\"KR\"]"));

    var response = patch(target.getId(), request, true);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().path("code").asText()).isEqualTo("TRIP_NOT_FOUND");
    assertUnchanged(target);
    assertUnchanged(original);
  }

  @Test
  void missingTripReturnsExisting404() throws Exception {
    var response = patch(UUID.randomUUID(), request(), true);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().path("code").asText()).isEqualTo("TRIP_NOT_FOUND");
    assertUnchanged(original);
  }

  @Test
  void authenticationRemainsRequired() throws Exception {
    var response = patch(original.getId(), request(), false);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertUnchanged(original);
  }

  @Test
  void swaggerDocumentsCountryUpdateSemantics() {
    JsonNode api = http.getForObject("/v3/api-docs", JsonNode.class);
    JsonNode schema = api.path("components").path("schemas").path("TripUpdateRequest");
    assertThat(schema.path("properties").path("countries").path("type").asText()).isEqualTo("array");
    assertThat(schema.path("properties").path("countries").path("description").asText())
        .contains("null", "[]");
    assertThat(StreamSupport.stream(schema.path("required").spliterator(), false)
        .map(JsonNode::asText).toList()).contains("name", "startDate", "endDate").doesNotContain("countries");
    assertThat(api.path("paths").path("/api/v1/trips/{tripId}").path("patch")
        .path("description").asText()).contains("countries", "null", "[]", "404");
  }

  private User createUser() {
    return users.save(User.builder().provider(User.Provider.KAKAO)
        .providerId(UUID.randomUUID().toString()).name("tester").build());
  }

  private ObjectNode request() {
    return mapper.createObjectNode().put("name", "Updated")
        .put("startDate", "2026-11-12").put("endDate", "2026-11-25");
  }

  private ResponseEntity<JsonNode> patch(UUID id, ObjectNode request, boolean authenticated)
      throws Exception {
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (authenticated) {
      headers.setBearerAuth(accessToken);
    }
    return http.exchange("/api/v1/trips/" + id, HttpMethod.PATCH,
        new HttpEntity<>(mapper.writeValueAsString(request), headers), JsonNode.class);
  }

  private JsonNode get(String endpoint) {
    var headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    var response = http.exchange(endpoint, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return response.getBody();
  }

  private void assertUnchanged(Trip expected) {
    Trip saved = trips.findById(expected.getId()).orElseThrow();
    assertThat(saved.getName()).isEqualTo(expected.getName());
    assertThat(saved.getStartDate()).isEqualTo(expected.getStartDate());
    assertThat(saved.getEndDate()).isEqualTo(expected.getEndDate());
    assertThat(saved.getCountries()).containsExactlyElementsOf(expected.getCountries());
    assertThat(saved.getStatus()).isEqualTo(expected.getStatus());
    assertThat(saved.getConcerns()).containsExactlyElementsOf(expected.getConcerns());
    assertThat(saved.getUpdatedAt()).isEqualTo(expected.getUpdatedAt());
  }
}
