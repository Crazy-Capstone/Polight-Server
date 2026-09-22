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
import polight.server.domain.chat.dto.ChatHistoryResponse.Message;
import polight.server.domain.chat.dto.ChatMessageMetadata;
import polight.server.domain.chat.dto.RagQueryRequest.HistoryTurn;
import polight.server.domain.chat.dto.RagQueryResponse;
import polight.server.domain.chat.entity.ChatMessage;
import polight.server.domain.terms.entity.PolicyTermsChunk;

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
   * policy_terms_chunks}에 있으므로 서버가 붙인다.
   *
   * @param scopedChunks 질의에 지목한 약관에 속한 것으로 확인된 청크. 여기 없는 {@code chunkId}는 위치 없이 인용문만 남긴다
   */
  public List<SourceResponse> toSources(
      RagQueryResponse response, List<PolicyTermsChunk> scopedChunks) {
    if (response.sources() == null || response.sources().isEmpty()) {
      return List.of();
    }

    Map<UUID, PolicyTermsChunk> byId =
        scopedChunks.stream()
            .collect(Collectors.toMap(PolicyTermsChunk::getId, Function.identity()));

    return response.sources().stream()
        .map(source -> toSource(source, byId.get(source.chunkId())))
        .toList();
  }

  private SourceResponse toSource(RagQueryResponse.Source source, PolicyTermsChunk chunk) {
    if (chunk == null) {
      // AI가 보낸 chunkId를 DB에서 찾지 못했거나 질의에 지목한 약관 밖의 청크였다. 인용문은
      // AI 응답에 이미 들어 있으므로 버리지 않고, 위치만 비운다.
      return new SourceResponse(
          source.chunkId(), source.documentId(), null, null, source.page(), source.page(),
          source.quote());
    }

    return new SourceResponse(
        chunk.getId(),
        // 약관 청크에는 문서가 없다. 공용 약관은 원본 문서(source_document_id)가 비어 있을 수
        // 있고, 있더라도 그것은 "누가 올린 파일인가"라서 근거의 위치가 아니다. AI가 보낸 값을
        // 그대로 흘린다 -- 없으면 null 이고, 응답 스키마는 그대로다.
        source.documentId(),
        chunk.getSectionTitle(),
        chunk.getClausePath(),
        chunk.getPageStart(),
        chunk.getPageEnd(),
        source.quote());
  }

  /**
   * 저장된 메시지를 화면용으로 바꾼다. 오래된 것부터 놓는다.
   *
   * <p>근거는 {@code metadata_json}에서 되꺼낸다. 질문·답변 응답과 같은 모양으로 내려주면 프론트가 두 경로를 다르게 다룰 필요가 없다.
   */
  public List<Message> toMessages(List<ChatMessage> recentFirst) {
    List<ChatMessage> oldestFirst = new ArrayList<>(recentFirst);
    Collections.reverse(oldestFirst);

    return oldestFirst.stream().map(this::toMessage).toList();
  }

  private Message toMessage(ChatMessage message) {
    return new Message(
        message.getId(),
        message.getSender(),
        message.getContent(),
        message.getResponseType(),
        readSources(message.getMetadataJson()),
        message.getCreatedAt());
  }

  /**
   * 저장해 둔 근거를 되읽는다.
   *
   * <p>읽지 못해도 빈 목록으로 넘긴다. 근거는 부가 정보인데, 이것 때문에 대화 이력 전체가 열리지 않으면 사용자는 자기 대화를 볼 수 없게 된다.
   */
  private List<SourceResponse> readSources(String metadataJson) {
    if (metadataJson == null || metadataJson.isBlank()) {
      return List.of();
    }

    try {
      ChatMessageMetadata metadata =
          objectMapper.readValue(metadataJson, ChatMessageMetadata.class);
      return metadata.sources() == null ? List.of() : metadata.sources();
    } catch (JsonProcessingException exception) {
      log.warn("대화 메타데이터를 읽지 못해 근거 없이 내려줍니다.", exception);
      return List.of();
    }
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
