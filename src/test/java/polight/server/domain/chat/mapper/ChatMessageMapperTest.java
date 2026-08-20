package polight.server.domain.chat.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import polight.server.domain.chat.dto.ChatAnswerResponse.SourceResponse;
import polight.server.domain.chat.dto.RagQueryRequest.HistoryTurn;
import polight.server.domain.chat.dto.RagQueryResponse;
import polight.server.domain.chat.entity.ChatMessage;
import polight.server.domain.chat.entity.ChatSender;

class ChatMessageMapperTest {

  private final ChatMessageMapper mapper = new ChatMessageMapper(new ObjectMapper());

  @Test
  void turnsRecentFirstMessagesIntoOldestFirstHistory() {
    List<ChatMessage> recentFirst =
        List.of(
            message(ChatSender.ASSISTANT, "4시간 이상 지연 시…"),
            message(ChatSender.USER, "항공편 지연되면 보상돼요?"));

    List<HistoryTurn> history = mapper.toHistory(recentFirst);

    // LLM은 대화를 시간 순으로 읽는다. 조회는 최신순이므로 뒤집어야 한다.
    assertThat(history)
        .extracting(HistoryTurn::sender, HistoryTurn::content)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("USER", "항공편 지연되면 보상돼요?"),
            org.assertj.core.groups.Tuple.tuple("ASSISTANT", "4시간 이상 지연 시…"));
  }

  @Test
  void keepsQuoteWhenChunkIsNotOwned() {
    UUID chunkId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    RagQueryResponse response =
        new RagQueryResponse(
            "보상됩니다.",
            "TEXT",
            List.of(new RagQueryResponse.Source(chunkId, documentId, 12, "항공기 지연으로 인하여…")));

    List<SourceResponse> sources = mapper.toSources(response, List.of());

    // 청크를 못 찾았거나 남의 것이면 위치만 비운다. 인용문은 AI 응답에 이미 있으므로 버리지 않는다.
    assertThat(sources).hasSize(1);
    assertThat(sources.get(0).quote()).isEqualTo("항공기 지연으로 인하여…");
    assertThat(sources.get(0).sectionTitle()).isNull();
    assertThat(sources.get(0).clausePath()).isNull();
    assertThat(sources.get(0).pageStart()).isEqualTo(12);
  }

  @Test
  void returnsEmptySourcesWhenAiOmitsThem() {
    assertThat(mapper.toSources(new RagQueryResponse("모르겠습니다.", "TEXT", null), List.of()))
        .isEmpty();
  }

  @Test
  void serializesMetadataWithLatency() {
    String json =
        mapper.toMetadataJson(
            List.of(new SourceResponse(null, null, "제3관", "제3관 > 제12조", 12, 12, "인용")), 1234L);

    assertThat(json).contains("\"latencyMs\":1234").contains("제3관 > 제12조");
  }

  @Test
  void returnsNullMetadataWhenSerializationFails() {
    ChatMessageMapper failing =
        new ChatMessageMapper(
            new ObjectMapper() {
              @Override
              public String writeValueAsString(Object value)
                  throws com.fasterxml.jackson.core.JsonProcessingException {
                throw new com.fasterxml.jackson.core.JsonProcessingException("boom") {};
              }
            });

    // 근거는 부가 정보다. 직렬화가 실패해도 사용자가 받은 답을 잃을 이유가 없다.
    assertThat(failing.toMetadataJson(List.of(), 1L)).isNull();
  }

  private ChatMessage message(ChatSender sender, String content) {
    return ChatMessage.builder().sender(sender).content(content).build();
  }
}
