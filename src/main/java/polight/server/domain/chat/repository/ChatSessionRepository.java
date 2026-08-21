package polight.server.domain.chat.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import polight.server.domain.chat.entity.ChatSession;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

  /** 여행당 세션은 하나다. 유니크 인덱스로 보장한다(V7). */
  Optional<ChatSession> findByUserIdAndTripId(UUID userId, UUID tripId);
}
