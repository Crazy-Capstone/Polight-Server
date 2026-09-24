package polight.server.domain.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import polight.server.domain.chat.dto.ChatAnswerResponse.SourceResponse;
import polight.server.domain.chat.entity.ChatResponseType;
import polight.server.domain.chat.entity.ChatSender;

/**
 * 여행의 대화 이력.
 *
 * @param sessionId 아직 대화가 없으면 {@code null}이다. 세션은 첫 질문에 만들어진다
 * @param messages 오래된 것부터. 화면에 위에서 아래로 그리는 순서다
 */
public record ChatHistoryResponse(
    @Schema(description = "대화가 없으면 null") UUID sessionId, List<Message> messages) {

  /** 아직 대화가 없는 여행. 세션이 없으므로 sessionId 도 비어 있다. */
  public static ChatHistoryResponse empty() {
    return new ChatHistoryResponse(null, List.of());
  }

  /**
   * 저장된 메시지 1건.
   *
   * @param sender {@code USER}면 사용자 말풍선, {@code ASSISTANT}면 챗봇 말풍선이다
   * @param suggestedContacts 함께 띄울 현지 연락처 종류. 사용자 메시지에는 항상 비어 있다
   * @param sources 답변의 근거. 사용자 메시지에는 항상 비어 있다
   * @param createdAt 말풍선에 붙일 시각
   */
  public record Message(
      UUID messageId,
      ChatSender sender,
      String content,
      ChatResponseType responseType,
      @Schema(
              description =
                  "함께 띄울 현지 연락처 종류. 사용자 메시지와 이 필드가 붙기 전 메시지는 빈 배열. "
                      + "현재 알려진 값은 HOSPITAL, POLICE, EMBASSY 이며 늘어날 수 있다 "
                      + "-- 모르는 값은 그리지 않고 넘기면 된다",
              example = "[\"POLICE\"]")
          List<String> suggestedContacts,
      List<SourceResponse> sources,
      LocalDateTime createdAt) {}
}
