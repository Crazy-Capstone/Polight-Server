package polight.server.domain.trip.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import polight.server.domain.trip.entity.Trip;

public interface TripRepository extends JpaRepository<Trip, UUID> {

  List<Trip> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

  Optional<Trip> findByIdAndUserId(UUID id, UUID userId);
}
