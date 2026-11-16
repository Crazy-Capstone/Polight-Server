package polight.server.domain.consent.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import polight.server.domain.consent.entity.ConsentHistory;
import polight.server.domain.consent.entity.ConsentType;
import polight.server.domain.user.entity.User;

public interface ConsentHistoryRepository extends JpaRepository<ConsentHistory, Long> {

  List<ConsentHistory> findByUserIdOrderByIdDesc(UUID userId);

  Optional<ConsentHistory> findFirstByUserIdAndConsentTypeOrderByIdDesc(
      UUID userId, ConsentType consentType);

  @Query("""
      select h from ConsentHistory h
      where h.user.id = :userId
        and h.id = (
          select max(other.id) from ConsentHistory other
          where other.user.id = h.user.id and other.consentType = h.consentType
        )
      """)
  List<ConsentHistory> findLatestByUserId(@Param("userId") UUID userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id = :userId")
  Optional<User> lockUserForRecording(@Param("userId") UUID userId);
}
