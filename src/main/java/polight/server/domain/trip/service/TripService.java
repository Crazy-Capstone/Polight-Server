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
import polight.server.domain.trip.mapper.TripMapper;
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
  private final TripMapper tripMapper;

  @Transactional
  public TripResponse createTrip(UUID userId, TripCreateRequest request) {
    return tripMapper.toResponse(createTripEntity(userId, request));
  }

  /**
   * 여행을 생성하고 엔티티를 돌려준다.
   *
   * <p>같은 트랜잭션에서 여행과 약관 문서를 함께 만들 때, 방금 저장한 여행을 다시 조회하지 않고 그대로 넘기기 위해 사용한다. 응답 DTO가 필요하면 {@link
   * #createTrip}을 쓴다.
   */
  @Transactional
  public Trip createTripEntity(UUID userId, TripCreateRequest request) {
    validateTripPeriod(request.startDate(), request.endDate());

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    return tripRepository.save(tripMapper.toEntity(user, request));
  }

  public List<TripResponse> getTrips(UUID userId) {
    return tripMapper.toResponses(tripRepository.findAllByUserIdOrderByCreatedAtDesc(userId));
  }

  public TripResponse getTrip(UUID userId, UUID tripId) {
    return tripMapper.toResponse(getOwnedTrip(userId, tripId));
  }

  @Transactional
  public TripResponse updateTrip(UUID userId, UUID tripId, TripUpdateRequest request) {
    validateTripPeriod(request.startDate(), request.endDate());

    Trip trip = getOwnedTrip(userId, tripId);
    trip.update(request.name().trim(), request.startDate(), request.endDate());

    return tripMapper.toResponse(trip);
  }

  /**
   * 소유권을 확인한 여행 엔티티를 돌려준다.
   *
   * <p>다른 도메인 서비스가 "이 사용자의 여행이 맞는지" 확인하면서 엔티티를 함께 얻기 위해 호출한다. 응답 DTO를 돌려주는 {@link #getTrip}과 용도가
   * 다르다.
   */
  public Trip getOwnedTrip(UUID userId, UUID tripId) {
    return tripRepository
        .findByIdAndUserId(tripId, userId)
        .orElseThrow(() -> new BaseException(ErrorCode.TRIP_NOT_FOUND));
  }

  private void validateTripPeriod(LocalDate startDate, LocalDate endDate) {
    if (endDate.isBefore(startDate)) {
      throw new BaseException(ErrorCode.INVALID_TRIP_PERIOD);
    }
  }
}
