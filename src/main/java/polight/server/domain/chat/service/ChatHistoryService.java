package polight.server.domain.chat.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.chat.dto.ChatHistoryResponse;
import polight.server.domain.chat.entity.ChatSession;
import polight.server.domain.chat.mapper.ChatMessageMapper;

/**
 * 여행의 대화 이력을 돌려준다.
 *
 * <p>대화를 서버가 저장하기로 했으니 읽는 경로도 서버가 준다. 프론트가 화면에 들고 있게 하면 앱을 껐다 켤 때 대화가 사라지고, DB에 남은 기록은 아무도 볼 수 없게
 * 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatHistoryService {

  /**
   * 한 번에 돌려줄 최대 메시지 수.
   *
   * <p>세션이 여행당 하나이고 닫는 시점이 없어 대화가 계속 쌓인다. 상한이 없으면 오래 쓴 여행에서 응답이 수 MB가 된다.
   *
   * <p>커서 페이지네이션은 두지 않았다. 프론트에 "더 보기"가 없고, MVP에서 필요한 것은 최근 대화를 다시 보여주는 것뿐이다.
   */
  private static final int MAX_LIMIT = 200;

  private static final int DEFAULT_LIMIT = 50;

  private final ChatSessionService chatSessionService;
  private final ChatMessageService chatMessageService;
  private final ChatMessageMapper chatMessageMapper;

  /**
   * 최근 메시지를 오래된 것부터 돌려준다.
   *
   * <p>대화가 아직 없으면 빈 목록이다. 오류가 아니다 -- 여행을 만들고 챗봇을 열지 않은 상태가 정상이다.
   *
   * @param limit 1보다 작거나 {@value #MAX_LIMIT}보다 크면 범위 안으로 맞춘다
   */
  public ChatHistoryResponse getMessages(UUID userId, UUID tripId, Integer limit) {
    // 여행 소유권은 세션 조회 쪽에서 확인한다. 남의 여행이면 TRIP_NOT_FOUND 다.
    ChatSession session = chatSessionService.findSession(userId, tripId).orElse(null);
    if (session == null) {
      return ChatHistoryResponse.empty();
    }

    return new ChatHistoryResponse(
        session.getId(),
        chatMessageMapper.toMessages(
            chatMessageService.loadRecent(session.getId(), clamp(limit))));
  }

  private static int clamp(Integer limit) {
    if (limit == null) {
      return DEFAULT_LIMIT;
    }
    return Math.min(Math.max(limit, 1), MAX_LIMIT);
  }
}
