package polight.server.domain.trip.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.trip.dto.TripCreateRequest;
import polight.server.domain.trip.dto.TripResponse;
import polight.server.domain.trip.dto.TripUpdateRequest;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.trip.repository.TripRepository;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.repository.UserRepository;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripService {

  private final TripRepository tripRepository;
  private final UserRepository userRepository;

  @Transactional
  public TripResponse create(UUID userId, TripCreateRequest request) {
    validateDates(request.startDate(), request.endDate());
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
    Trip trip =
        tripRepository.save(
            Trip.builder()
                .user(user)
                .name(request.name().trim())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .build());
    return TripResponse.from(trip);
  }

  public List<TripResponse> findAll(UUID userId) {
    return tripRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(TripResponse::from)
        .toList();
  }

  public TripResponse find(UUID userId, UUID tripId) {
    return TripResponse.from(getOwnedTrip(userId, tripId));
  }

  @Transactional
  public TripResponse update(UUID userId, UUID tripId, TripUpdateRequest request) {
    validateDates(request.startDate(), request.endDate());
    Trip trip = getOwnedTrip(userId, tripId);
    trip.update(request.name().trim(), request.startDate(), request.endDate());
    return TripResponse.from(trip);
  }

  public Trip getOwnedTrip(UUID userId, UUID tripId) {
    return tripRepository
        .findByIdAndUserId(tripId, userId)
        .orElseThrow(() -> new BaseException(ErrorCode.TRIP_NOT_FOUND));
  }

  private void validateDates(LocalDate startDate, LocalDate endDate) {
    if (endDate.isBefore(startDate)) {
      throw new BaseException(ErrorCode.INVALID_TRIP_PERIOD);
    }
  }
}
