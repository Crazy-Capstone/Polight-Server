package polight.server.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import polight.server.domain.chat.dto.ChatHistoryResponse;
import polight.server.domain.chat.dto.ChatHistoryResponse.Message;
import polight.server.domain.chat.entity.ChatMessage;
import polight.server.domain.chat.entity.ChatResponseType;
import polight.server.domain.chat.entity.ChatSender;
import polight.server.domain.chat.entity.ChatSession;
import polight.server.domain.chat.mapper.ChatMessageMapper;

@ExtendWith(MockitoExtension.class)
class ChatHistoryServiceTest {

  @Mock private ChatSessionService chatSessionService;
  @Mock private ChatMessageService chatMessageService;
  @Mock private ChatMessageMapper chatMessageMapper;
  @Mock private ChatSession session;

  @InjectMocks private ChatHistoryService service;

  private final UUID userId = UUID.randomUUID();
  private final UUID tripId = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();

  @Test
  void returnsEmptyHistoryWhenChatNeverStarted() {
    given(chatSessionService.findSession(userId, tripId)).willReturn(Optional.empty());

    ChatHistoryResponse response = service.getMessages(userId, tripId, null);

    // 여행을 만들고 챗봇을 열지 않은 상태는 정상이다. 오류로 돌려주지 않는다.
    assertThat(response.sessionId()).isNull();
    assertThat(response.messages()).isEmpty();
  }

  @Test
  void returnsMessagesWithSessionId() {
    givenSessionExists();
    given(chatMessageService.loadRecent(eq(sessionId), anyInt()))
        .willReturn(List.of(message(ChatSender.USER, "보상돼요?")));
    given(chatMessageMapper.toMessages(anyList()))
        .willReturn(
            List.of(
                new Message(
                    UUID.randomUUID(),
                    ChatSender.USER,
                    "보상돼요?",
                    ChatResponseType.TEXT,
                    List.of(),
                    LocalDateTime.now())));

    ChatHistoryResponse response = service.getMessages(userId, tripId, null);

    assertThat(response.sessionId()).isEqualTo(sessionId);
    assertThat(response.messages()).hasSize(1);
  }

  @Test
  void usesDefaultLimitWhenNotGiven() {
    givenSessionExists();

    service.getMessages(userId, tripId, null);

    verify(chatMessageService).loadRecent(sessionId, 50);
  }

  @Test
  void clampsLimitIntoAllowedRange() {
    givenSessionExists();

    service.getMessages(userId, tripId, 5000);
    service.getMessages(userId, tripId, 0);
    service.getMessages(userId, tripId, -3);

    // 세션이 여행당 하나이고 닫는 시점이 없어 대화가 계속 쌓인다. 상한 없이 열면 응답이 수 MB가 된다.
    verify(chatMessageService).loadRecent(sessionId, 200);
    verify(chatMessageService, org.mockito.Mockito.times(2)).loadRecent(sessionId, 1);
  }

  private void givenSessionExists() {
    given(session.getId()).willReturn(sessionId);
    given(chatSessionService.findSession(userId, tripId)).willReturn(Optional.of(session));
  }

  private ChatMessage message(ChatSender sender, String content) {
    return ChatMessage.builder().session(session).sender(sender).content(content).build();
  }
}
