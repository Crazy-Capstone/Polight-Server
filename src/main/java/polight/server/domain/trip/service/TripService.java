package polight.server.domain.trip.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.trip.dto.TripDto;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.trip.entity.TripStatus;
import polight.server.domain.trip.repository.TripRepository;
import polight.server.global.util.DisplayUtils;

@Service
@RequiredArgsConstructor
public class TripService {
  private final TripRepository tripRepository;
  private final Clock clock;

  @Transactional(readOnly = true)
  public TripDto.Response getCurrent(UUID userId) {
    return findCurrentEntity(userId).map(this::toResponse).orElse(null);
  }

  @Transactional(readOnly = true)
  public Optional<Trip> findCurrentEntity(UUID userId) {
    LocalDate today = LocalDate.now(clock);
    return tripRepository.findFirstByUserIdAndStatusOrderByStartDateDesc(userId, TripStatus.ACTIVE)
        .or(() -> tripRepository
            .findFirstByUserIdAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
                userId, today, today));
  }

  @Transactional(readOnly = true)
  public List<TripDto.Response> getTrips(UUID userId, TripStatus status) {
    List<Trip> trips = status == null
        ? tripRepository.findByUserIdOrderByStartDateDesc(userId)
        : tripRepository.findByUserIdAndStatusOrderByStartDateDesc(userId, status);
    return trips.stream().map(this::toResponse).toList();
  }

  public TripDto.Response toResponse(Trip trip) {
    LocalDate today = LocalDate.now(clock);
    return new TripDto.Response(trip.getId(), trip.getTitle(), trip.getCountryCode(),
        trip.getCountryName(), trip.getCityName(), trip.getFlagEmoji(), trip.getStartDate(),
        trip.getEndDate(), DisplayUtils.dDayLabel(trip.getStartDate(), today), trip.getStatus());
  }
}
