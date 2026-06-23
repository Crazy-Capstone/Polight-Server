package polight.server.domain.trip.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.trip.entity.TripStatus;

public interface TripRepository extends JpaRepository<Trip, UUID> {

  List<Trip> findByUserIdOrderByStartDateDesc(UUID userId);

  List<Trip> findByUserIdAndStatusOrderByStartDateDesc(UUID userId, TripStatus status);

  Optional<Trip> findByIdAndUserId(UUID id, UUID userId);

  Optional<Trip> findFirstByUserIdAndStatusOrderByStartDateDesc(UUID userId, TripStatus status);

  Optional<Trip>
      findFirstByUserIdAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
          UUID userId, java.time.LocalDate startDate, java.time.LocalDate endDate);
}
