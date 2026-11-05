package polight.server.domain.trip.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import polight.server.domain.trip.entity.Trip;

public interface TripRepository extends JpaRepository<Trip, UUID> {

  List<Trip> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

  Optional<Trip> findByIdAndUserId(UUID id, UUID userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from Trip t where t.id = :tripId and t.user.id = :userId")
  Optional<Trip> findOwnedByIdForUpdate(@Param("tripId") UUID tripId, @Param("userId") UUID userId);
}
