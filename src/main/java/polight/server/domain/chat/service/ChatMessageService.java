package polight.server.domain.chat.service;

import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.chat.entity.ChatMessage;
import polight.server.domain.chat.entity.ChatResponseType;
import polight.server.domain.chat.entity.ChatSender;
import polight.server.domain.chat.entity.ChatSession;
import polight.server.domain.chat.repository.ChatMessageRepository;
import polight.server.domain.chat.repository.ChatSessionRepository;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

/**
 * 대화 메시지를 읽고 쓴다.
 *
 * <p>메시지 저장을 AI 서버에 넘기지 않는 이유는 세 가지다. {@code chat_messages}에는 테넌트 격리가 없어 AI 계정에 SELECT를 주면 전체 사용자
 * 대화에 접근하게 되고, AI에는 JWT 신원이 없어 페이로드의 {@code userId}를 검증할 방법이 없고, 스키마 소유권이 Flyway 한 곳에 있어야 한다.
 *
 * <p>쓰기 메서드를 질의 오케스트레이션({@link ChatQueryService})과 분리해 둔 것은 트랜잭션 경계 때문이다. LLM 호출을 트랜잭션 안에 두면 응답까지
 * DB 커넥션을 잡고 있게 되어, 동시 질문이 풀 크기를 넘기는 순간 전부 대기한다.
 */
@Service
@Transactional(readOnly = true)
public class ChatMessageService {

  private final ChatMessageRepository chatMessageRepository;
  private final ChatSessionRepository chatSessionRepository;

  private final int historySize;

  public ChatMessageService(
      ChatMessageRepository chatMessageRepository,
      ChatSessionRepository chatSessionRepository,
      @Value("${ai.server.chat-history-size}") int historySize) {
    this.chatMessageRepository = chatMessageRepository;
    this.chatSessionRepository = chatSessionRepository;
    this.historySize = historySize;
  }

  /**
   * AI에 실어 보낼 직전 대화. 최신순으로 온다.
   *
   * <p>이 질문을 저장하기 전에 불러야 한다. 저장한 뒤에 부르면 방금 넣은 질문이 이력에 섞여 같은 문장이 두 번 프롬프트에 들어간다.
   */
  public List<ChatMessage> loadRecentHistory(UUID sessionId) {
    return loadRecent(sessionId, historySize);
  }

  /**
   * 세션의 최근 메시지를 개수만큼. 최신순으로 온다.
   *
   * <p>AI에 보낼 이력과 화면에 그릴 이력은 자르는 개수가 다르다. 전자는 프롬프트 길이를 맞추려고 6개로 고정하고, 후자는 사용자가 스크롤해 볼 만큼 필요하다.
   */
  public List<ChatMessage> loadRecent(UUID sessionId, int limit) {
    return chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(sessionId, Limit.of(limit));
  }

  /**
   * 사용자 질문을 저장한다.
   *
   * <p>AI 호출 전에 커밋한다. 답변을 받은 뒤에 함께 저장하면, LLM이 실패했을 때 사용자가 보낸 질문까지 사라져 화면에서 메시지가 없어진 것처럼 보인다.
   */
  @Transactional
  public void appendUserMessage(UUID sessionId, String content) {
    chatMessageRepository.save(
        ChatMessage.builder()
            .session(getSession(sessionId))
            .sender(ChatSender.USER)
            .content(content)
            .responseType(ChatResponseType.TEXT)
            .build());
  }

  /** 답변을 저장하고 세션을 활성 상태로 갱신한다. */
  @Transactional
  public ChatMessage appendAssistantMessage(UUID sessionId, String content, String metadataJson) {
    ChatSession session = getSession(sessionId);
    session.touch();

    return chatMessageRepository.save(
        ChatMessage.builder()
            .session(session)
            .sender(ChatSender.ASSISTANT)
            .content(content)
            // 카드형 4종은 렌더링에 필요한 데이터 출처가 아직 없어 TEXT 하나로 고정한다.
            .responseType(ChatResponseType.TEXT)
            .metadataJson(metadataJson)
            .build());
  }

  private ChatSession getSession(UUID sessionId) {
    return chatSessionRepository
        .findById(sessionId)
        .orElseThrow(() -> new BaseException(ErrorCode.CHAT_SESSION_NOT_FOUND));
  }
}
