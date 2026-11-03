package polight.server.domain.trip;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import polight.server.domain.trip.dto.TripCreateRequest;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.trip.mapper.TripMapper;
import polight.server.domain.user.entity.User;

class TripCountriesTest {

  private static ValidatorFactory factory;
  private static Validator validator;
  private static final LocalDate START = LocalDate.of(2026, 11, 10);
  private static final LocalDate END = LocalDate.of(2026, 11, 20);

  @BeforeAll
  static void configureValidation() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void closeValidation() {
    factory.close();
  }

  @Test
  void entityRemovesDuplicatesAndKeepsSelectionOrder() {
    var countries = new ArrayList<>(List.of("FR", "JP", "FR", "KR", "JP"));
    Trip trip = Trip.builder().countries(countries).build();
    countries.clear();
    assertThat(trip.getCountries()).containsExactly("FR", "JP", "KR");
  }

  @Test
  void entityDefaultsMissingCountriesToEmptyList() {
    assertThat(Trip.builder().build().getCountries()).isEmpty();
    assertThat(Trip.builder().countries(List.of()).build().getCountries()).isEmpty();
  }

  @Test
  void mapperPersistsCountriesAlongsideExistingFields() {
    Trip trip = new TripMapper().toEntity(User.builder().name("tester").build(),
        request(List.of("JP", "KR", "JP")));
    assertThat(trip.getName()).isEqualTo("Trip");
    assertThat(trip.getStartDate()).isEqualTo(START);
    assertThat(trip.getEndDate()).isEqualTo(END);
    assertThat(trip.getCountries()).containsExactly("JP", "KR");
    assertThat(trip.getConcerns()).isEmpty();
  }

  @Test
  void previousJsonRequestWithoutCountriesStillDeserializesAndValidates() throws Exception {
    var mapper = new ObjectMapper().findAndRegisterModules();
    TripCreateRequest request = mapper.readValue("""
        {"name":"Trip","startDate":"2026-11-10","endDate":"2026-11-20","concerns":[]}
        """, TripCreateRequest.class);
    assertThat(validator.validate(request)).isEmpty();
    assertThat(request.countriesOrEmpty()).isEmpty();
  }

  @Test
  void nullAndEmptyCountryListsAreAllowedOnCreation() {
    assertThat(validator.validate(request(null))).isEmpty();
    assertThat(validator.validate(request(List.of()))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"JP", "KR", "FR", "US", "GB", "DE"})
  void acceptsIsoAlpha2Codes(String country) {
    assertThat(validator.validate(request(List.of(country)))).isEmpty();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " ", "jp", "JP ", " JP", "JPN", "J", "ZZ", "UK", "XK", "12"})
  void rejectsInvalidCountryElements(String country) {
    var violations = validator.validate(request(Collections.singletonList(country)));
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString()).startsWith("countries[0]");
  }

  private TripCreateRequest request(List<String> countries) {
    return new TripCreateRequest(" Trip ", START, END, List.of(), null, countries);
  }
}
