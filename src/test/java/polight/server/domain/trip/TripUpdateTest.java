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
import polight.server.domain.concern.entity.Concern;
import polight.server.domain.trip.dto.TripUpdateRequest;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.trip.entity.TripStatus;

class TripUpdateTest {

  private static final LocalDate START = LocalDate.of(2026, 11, 10);
  private static final LocalDate END = LocalDate.of(2026, 11, 20);
  private static ValidatorFactory factory;
  private static Validator validator;

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
  void replacesCountriesWithoutChangingStatusOrConcerns() {
    Trip trip = trip();
    var countries = new ArrayList<>(List.of("KR", "JP", "KR", "FR"));

    trip.update("Updated", START.plusDays(1), END.plusDays(2), countries);
    countries.clear();

    assertThat(trip.getName()).isEqualTo("Updated");
    assertThat(trip.getStartDate()).isEqualTo(START.plusDays(1));
    assertThat(trip.getEndDate()).isEqualTo(END.plusDays(2));
    assertThat(trip.getCountries()).containsExactly("KR", "JP", "FR");
    assertThat(trip.getStatus()).isEqualTo(TripStatus.ACTIVE);
    assertThat(trip.getConcerns()).containsExactly(Concern.FLIGHT_DELAY);
  }

  @Test
  void nullCountriesKeepExistingSelection() {
    Trip trip = trip();
    trip.update("Updated", START, END, null);
    assertThat(trip.getCountries()).containsExactly("FR", "JP");
  }

  @Test
  void oldUpdateMethodKeepsExistingSelection() {
    Trip trip = trip();
    trip.update("Updated", START, END);
    assertThat(trip.getName()).isEqualTo("Updated");
    assertThat(trip.getCountries()).containsExactly("FR", "JP");
  }

  @Test
  void emptyCountriesExplicitlyClearSelection() {
    Trip trip = trip();
    trip.update("Updated", START, END, List.of());
    assertThat(trip.getCountries()).isEmpty();
  }

  @Test
  void previousJsonRequestStillDeserializesAndValidates() throws Exception {
    var mapper = new ObjectMapper().findAndRegisterModules();
    TripUpdateRequest request = mapper.readValue("""
        {"name":"Trip","startDate":"2026-11-10","endDate":"2026-11-20"}
        """, TripUpdateRequest.class);
    assertThat(request.countries()).isNull();
    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void validAndEmptyCountryListsAreAccepted() {
    assertThat(validator.validate(request(List.of("JP", "KR", "JP")))).isEmpty();
    assertThat(validator.validate(request(List.of()))).isEmpty();
    assertThat(validator.validate(request(null))).isEmpty();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " ", "jp", "JP ", "JPN", "ZZ", "UK", "12"})
  void rejectsInvalidCountryElements(String country) {
    var violations = validator.validate(request(Collections.singletonList(country)));
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString()).startsWith("countries[0]");
  }

  private Trip trip() {
    return Trip.builder().name("Original").startDate(START).endDate(END)
        .countries(List.of("FR", "JP")).status(TripStatus.ACTIVE)
        .concerns(List.of(Concern.FLIGHT_DELAY)).build();
  }

  private TripUpdateRequest request(List<String> countries) {
    return new TripUpdateRequest("Trip", START, END, countries);
  }
}
