package polight.server.domain.chat.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import polight.server.domain.chat.entity.ChatSession;
import polight.server.domain.user.entity.User;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

  /** 여행당 세션은 하나다. 유니크 인덱스로 보장한다(V7). */
  Optional<ChatSession> findByUserIdAndTripId(UUID userId, UUID tripId);

  Optional<ChatSession> findByUserIdAndTripIsNull(UUID userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id = :userId")
  Optional<User> lockUnregisteredSessionOwner(@Param("userId") UUID userId);
}
