package polight.server.domain.chat.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import polight.server.domain.chat.dto.ChatAnswerResponse.SourceResponse;
import polight.server.domain.chat.dto.ChatMessageMetadata;
import polight.server.domain.chat.dto.RagQueryRequest.HistoryTurn;
import polight.server.domain.chat.dto.RagQueryResponse;
import polight.server.domain.chat.entity.ChatMessage;
import polight.server.domain.rag.entity.PolicyChunk;

/** 대화 메시지와 AI 응답 사이의 변환을 담당한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageMapper {

  private final ObjectMapper objectMapper;

  /**
   * 저장된 메시지를 AI에 보낼 이력으로 바꾼다.
   *
   * <p>조회는 최신순이므로 뒤집어 오래된 것부터 놓는다. LLM은 대화를 시간 순으로 읽는다.
   */
  public List<HistoryTurn> toHistory(List<ChatMessage> recentFirst) {
    List<ChatMessage> oldestFirst = new ArrayList<>(recentFirst);
    Collections.reverse(oldestFirst);

    return oldestFirst.stream()
        .map(message -> new HistoryTurn(message.getSender().name(), message.getContent()))
        .toList();
  }

  /**
   * AI가 돌려준 근거에 조항 위치를 채운다.
   *
   * <p>AI는 {@code chunkId}·{@code documentId}·{@code page}·{@code quote}만 보낸다. 조항 제목과 경로는 {@code
   * policy_chunks}에 있으므로 서버가 붙인다.
   *
   * @param ownedChunks 사용자 소유로 확인된 청크. 여기 없는 {@code chunkId}는 위치 없이 인용문만 남긴다
   */
  public List<SourceResponse> toSources(RagQueryResponse response, List<PolicyChunk> ownedChunks) {
    if (response.sources() == null || response.sources().isEmpty()) {
      return List.of();
    }

    Map<UUID, PolicyChunk> byId =
        ownedChunks.stream().collect(Collectors.toMap(PolicyChunk::getId, Function.identity()));

    return response.sources().stream()
        .map(source -> toSource(source, byId.get(source.chunkId())))
        .toList();
  }

  private SourceResponse toSource(RagQueryResponse.Source source, PolicyChunk chunk) {
    if (chunk == null) {
      // AI가 보낸 chunkId를 DB에서 찾지 못했거나 남의 청크였다. 인용문은 AI 응답에 이미 들어
      // 있으므로 버리지 않고, 위치만 비운다.
      return new SourceResponse(
          source.chunkId(), source.documentId(), null, null, source.page(), source.page(),
          source.quote());
    }

    return new SourceResponse(
        chunk.getId(),
        chunk.getDocument().getId(),
        chunk.getSectionTitle(),
        chunk.getClausePath(),
        chunk.getPageStart(),
        chunk.getPageEnd(),
        source.quote());
  }

  /**
   * {@code metadata_json}에 넣을 문자열.
   *
   * <p>직렬화가 실패해도 답변은 살린다. 근거는 부가 정보이고, 이것 때문에 사용자가 받은 답을 잃을 이유가 없다.
   */
  public String toMetadataJson(List<SourceResponse> sources, long latencyMs) {
    try {
      return objectMapper.writeValueAsString(new ChatMessageMetadata(sources, latencyMs));
    } catch (JsonProcessingException exception) {
      log.warn("대화 메타데이터를 직렬화하지 못해 비워 둡니다.", exception);
      return null;
    }
  }
}
