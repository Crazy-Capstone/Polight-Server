package polight.server.domain.chat.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import polight.server.domain.chat.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

  List<ChatMessage> findBySessionUserIdOrderByCreatedAtDesc(UUID userId);

  /**
   * 세션의 최근 메시지. 최신순이다.
   *
   * <p>AI 서버에 실어 보낼 이력을 여기서 자른다. 프롬프트 길이를 예측 가능하게 두기 위한 것이고, 개수는 {@code
   * ai.server.chat-history-size}로 조정한다.
   */
  List<ChatMessage> findBySessionIdOrderByCreatedAtDesc(UUID sessionId, Limit limit);
}
