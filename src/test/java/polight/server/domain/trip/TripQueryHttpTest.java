package polight.server.domain.trip;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
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
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.trip.repository.TripRepository;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.repository.UserRepository;
import polight.server.global.security.JwtTokenProvider;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:trip-query;MODE=PostgreSQL;NON_KEYWORDS=VALUE",
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
class TripQueryHttpTest {

  @Autowired private TestRestTemplate http;
  @Autowired private ObjectMapper mapper;
  @Autowired private UserRepository users;
  @Autowired private TripRepository trips;
  @Autowired private JwtTokenProvider tokens;
  private User owner;
  private User other;
  private String accessToken;

  @BeforeEach
  void createUsers() {
    owner = createUser();
    other = createUser();
    accessToken = tokens.createAccessToken(owner);
  }

  @Test
  void singleTripIncludesCountryOrderAndAllExistingFields() {
    Trip trip = createTrip(owner, List.of("FR", "JP", "KR"));

    var response = get("/api/v1/trips/" + trip.getId());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertTripResponse(response.getBody(), trip);
  }

  @Test
  void tripWithoutCountryInformationReturnsEmptyArray() {
    Trip trip = createTrip(owner, null);

    var response = get("/api/v1/trips/" + trip.getId());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertTripResponse(response.getBody(), trip);
    assertThat(response.getBody().path("countries").isArray()).isTrue();
    assertThat(response.getBody().path("countries").size()).isZero();
  }

  @Test
  void listIncludesCountriesForOwnTripsOnly() {
    Trip withCountries = createTrip(owner, List.of("JP", "KR"));
    Trip withoutCountries = createTrip(owner, null);
    createTrip(other, List.of("US"));

    var response = get("/api/v1/trips");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = response.getBody();
    assertThat(body.isArray()).isTrue();
    assertThat(body.size()).isEqualTo(2);
    assertThat(StreamSupport.stream(body.spliterator(), false)
        .map(node -> node.path("id").asText()).toList())
        .containsExactlyInAnyOrder(withCountries.getId().toString(), withoutCountries.getId().toString());
    for (JsonNode node : body) {
      Trip expected = node.path("id").asText().equals(withCountries.getId().toString())
          ? withCountries : withoutCountries;
      assertTripResponse(node, expected);
    }
  }

  @Test
  void listRemainsEmptyWhenOnlyOtherUsersHaveTrips() {
    createTrip(other, List.of("JP"));
    var response = get("/api/v1/trips");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().isArray()).isTrue();
    assertThat(response.getBody().size()).isZero();
  }

  @Test
  void anotherUsersTripIsHiddenAsNotFound() {
    Trip trip = createTrip(other, List.of("JP"));
    var response = get("/api/v1/trips/" + trip.getId());
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().path("code").asText()).isEqualTo("TRIP_NOT_FOUND");
    assertThat(response.getBody().has("countries")).isFalse();
  }

  @Test
  void missingTripStillReturnsExistingError() {
    var response = get("/api/v1/trips/" + UUID.randomUUID());
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().path("code").asText()).isEqualTo("TRIP_NOT_FOUND");
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/v1/trips", "/api/v1/trips/00000000-0000-0000-0000-000000000001"})
  void bothQueriesRequireAuthentication(String endpoint) {
    var response = http.getForEntity(endpoint, JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void swaggerIncludesCountriesInSharedResponseAndBothGetDescriptions() {
    JsonNode api = http.getForObject("/v3/api-docs", JsonNode.class);
    JsonNode properties = api.path("components").path("schemas").path("TripResponse").path("properties");
    assertThat(properties.has("id")).isTrue();
    assertThat(properties.has("name")).isTrue();
    assertThat(properties.has("startDate")).isTrue();
    assertThat(properties.has("endDate")).isTrue();
    assertThat(properties.has("status")).isTrue();
    assertThat(properties.has("concerns")).isTrue();
    assertThat(properties.has("createdAt")).isTrue();
    assertThat(properties.has("updatedAt")).isTrue();
    assertThat(properties.path("countries").path("type").asText()).isEqualTo("array");
    assertThat(properties.path("countries").path("items").path("type").asText()).isEqualTo("string");
    for (String endpoint : List.of("/api/v1/trips", "/api/v1/trips/{tripId}")) {
      assertThat(api.path("paths").path(endpoint).path("get").path("description").asText())
          .contains("countries");
    }
  }

  private User createUser() {
    return users.save(User.builder().provider(User.Provider.KAKAO)
        .providerId(UUID.randomUUID().toString()).name("tester").build());
  }

  private Trip createTrip(User user, List<String> countries) {
    return trips.save(Trip.builder().user(user).name("Trip")
        .startDate(LocalDate.of(2026, 11, 10)).endDate(LocalDate.of(2026, 11, 20))
        .countries(countries).build());
  }

  private ResponseEntity<JsonNode> get(String endpoint) {
    var headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    return http.exchange(endpoint, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
  }

  private void assertTripResponse(JsonNode body, Trip expected) {
    assertThat(body.path("id").asText()).isEqualTo(expected.getId().toString());
    assertThat(body.path("name").asText()).isEqualTo(expected.getName());
    assertThat(body.path("startDate").asText()).isEqualTo(expected.getStartDate().toString());
    assertThat(body.path("endDate").asText()).isEqualTo(expected.getEndDate().toString());
    assertThat(body.path("status").asText()).isEqualTo(expected.getStatus().name());
    assertThat(body.path("concerns")).isEqualTo(mapper.valueToTree(expected.getConcerns()));
    assertThat(body.path("countries")).isEqualTo(mapper.valueToTree(expected.getCountries()));
    assertThat(LocalDateTime.parse(body.path("createdAt").asText())).isNotNull();
    assertThat(LocalDateTime.parse(body.path("updatedAt").asText())).isNotNull();
  }
}
