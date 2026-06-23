package polight.server.domain.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.trip.entity.TripStatus;
import polight.server.domain.trip.repository.TripRepository;
import polight.server.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {
  @Mock TripRepository tripRepository;
  private TripService tripService;
  private final UUID userId = UUID.randomUUID();
  private final LocalDate today = LocalDate.of(2026, 6, 23);

  @BeforeEach
  void setUp() {
    tripService = new TripService(tripRepository,
        Clock.fixed(Instant.parse("2026-06-23T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void currentTrip_prioritizesActiveTripOverDateRangeTrip() {
    Trip active = trip(TripStatus.ACTIVE, today.plusDays(30), today.plusDays(40));
    given(tripRepository.findFirstByUserIdAndStatusOrderByStartDateDesc(userId, TripStatus.ACTIVE))
        .willReturn(Optional.of(active));

    var result = tripService.getCurrent(userId);

    assertThat(result.status()).isEqualTo(TripStatus.ACTIVE);
    verify(tripRepository, never())
        .findFirstByUserIdAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
            userId, today, today);
  }

  private Trip trip(TripStatus status, LocalDate start, LocalDate end) {
    User user = User.builder().name("user").provider(User.Provider.KAKAO).providerId("k").build();
    return Trip.builder().user(user).title("여행").countryCode("JP").countryName("일본")
        .startDate(start).endDate(end).status(status).build();
  }
}
