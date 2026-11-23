package polight.server.domain.consent.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import polight.server.domain.consent.entity.Consent;
import polight.server.domain.user.entity.User;

public interface ConsentRepository extends JpaRepository<Consent, UUID> {

  Optional<Consent> findByUserId(UUID userId);

  boolean existsByUserId(UUID userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id = :userId")
  Optional<User> lockUser(@Param("userId") UUID userId);
}
