package polight.server.domain.chat.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  /**
   * AI와 합의한 연락처 종류.
   *
   * <p>검증이 아니라 <b>관측</b>용이다. 여기 없는 값도 그대로 흘리고 로그만 남긴다({@link #toSuggestedContacts}).
   */
  private static final Set<String> KNOWN_CONTACT_KINDS = Set.of("HOSPITAL", "POLICE", "EMBASSY");

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
   * <p>조항 제목·경로·페이지·성격과 원문 전체는 {@code policy_terms_chunks}에서 붙인다. AI도 같은 값을 함께
   * 보내지만, 질의에 지목한 약관에 속한 것으로 확인된 청크가 있으면 그쪽이 우선이다.
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
      // AI가 보낸 chunkId를 DB에서 찾지 못했거나 질의에 지목한 약관 밖의 청크였다.
      //
      // 예전에는 위치를 통째로 비웠다. 이제 AI가 같은 값을 함께 보내므로 그것으로 채운다 --
      // 조항 제목도 원문도 없이 인용문만 떠 있는 것보다는 낫다. 다만 이 값들은 우리가 약관
      // 소속을 확인하지 못한 것이라 아래 분기의 값과 신뢰도가 같지 않고, clausePath 는 AI가
      // 보내지 않아 여전히 빈다. 이 경로로 들어오는 것 자체가 비정상이며
      // warnIfNoChunkResolved 가 서버 쪽에 흔적을 남긴다.
      return new SourceResponse(
          source.chunkId(),
          source.documentId(),
          source.index(),
          source.sectionTitle(),
          null,
          firstNonNull(source.pageStart(), source.page()),
          firstNonNull(source.pageEnd(), source.page()),
          source.clauseType(),
          source.quote(),
          source.text(),
          source.cited());
    }

    return new SourceResponse(
        chunk.getId(),
        // 약관 청크에는 문서가 없다. 공용 약관은 원본 문서(source_document_id)가 비어 있을 수
        // 있고, 있더라도 그것은 "누가 올린 파일인가"라서 근거의 위치가 아니다. AI가 보낸 값을
        // 그대로 흘린다 -- 없으면 null 이고, 응답 스키마는 그대로다.
        source.documentId(),
        // 답변 안에서 몇 번째 근거인가. 청크에는 없는 값이라 AI 것을 쓴다. chunk.chunkIndex 는
        // "약관 안에서 몇 번째 청크인가"라서 뜻이 다르다 -- 섞으면 안 된다.
        source.index(),
        chunk.getSectionTitle(),
        chunk.getClausePath(),
        chunk.getPageStart(),
        chunk.getPageEnd(),
        chunk.getClauseType() == null ? null : chunk.getClauseType().name(),
        source.quote(),
        // 조항 원문 전체. AI도 text 로 같은 것을 보내지만 DB 값을 쓴다 -- 질의에 지목한 약관에
        // 속한 것으로 이미 확인된 청크라 어느 약관의 본문인지가 보장된다.
        chunk.getContent(),
        source.cited());
  }

  /** 앞의 값을 쓰되 없으면 뒤의 값. AI가 pageStart/pageEnd 를 보내기 전에는 page 하나만 온다. */
  private static Integer firstNonNull(Integer preferred, Integer fallback) {
    return preferred != null ? preferred : fallback;
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
    ChatMessageMetadata metadata = readMetadata(message.getMetadataJson());

    return new Message(
        message.getId(),
        message.getSender(),
        message.getContent(),
        message.getResponseType(),
        metadata == null || metadata.suggestedContacts() == null
            ? List.of()
            : metadata.suggestedContacts(),
        metadata == null || metadata.sources() == null ? List.of() : metadata.sources(),
        message.getCreatedAt());
  }

  /**
   * AI가 보낸 연락처 종류를 응답에 실을 형태로 고른다.
   *
   * <p>합의한 3종({@code HOSPITAL} {@code POLICE} {@code EMBASSY}) 밖의 값이 와도 <b>버리지 않고 그대로
   * 흘린다.</b> 프론트가 모르는 값은 그리지 않을 뿐이고, 여기서 막으면 AI가 4번째 종류를 배포하는 순간 백엔드도
   * 같이 배포해야 그 값이 화면에 닿는다. 대신 {@code WARN}으로 드러낸다 -- 어휘가 늘어난 사실을 아무도 모르는
   * 것이 문제지, 값이 지나가는 것이 문제가 아니다.
   *
   * <p>{@code null}은 빈 목록으로 바꾼다. AI가 생략할 수 있고, 프론트가 {@code null}과 빈 배열을 나눠 다룰
   * 이유가 없다.
   */
  public List<String> toSuggestedContacts(RagQueryResponse response) {
    List<String> contacts = response.suggestedContacts();
    if (contacts == null || contacts.isEmpty()) {
      return List.of();
    }

    List<String> unknown = contacts.stream().filter(kind -> !KNOWN_CONTACT_KINDS.contains(kind)).toList();
    if (!unknown.isEmpty()) {
      log.warn("합의한 어휘 밖의 연락처 종류입니다(그대로 전달합니다): {}", unknown);
    }

    return contacts;
  }

  /**
   * 저장해 둔 메타데이터를 되읽는다.
   *
   * <p>읽지 못하면 {@code null}이다. 호출한 쪽이 빈 값으로 채운다 -- 근거와 연락처는 부가 정보인데, 이것 때문에
   * 대화 이력 전체가 열리지 않으면 사용자는 자기 대화를 볼 수 없게 된다.
   */
  private ChatMessageMetadata readMetadata(String metadataJson) {
    if (metadataJson == null || metadataJson.isBlank()) {
      return null;
    }

    try {
      return objectMapper.readValue(metadataJson, ChatMessageMetadata.class);
    } catch (JsonProcessingException exception) {
      log.warn("대화 메타데이터를 읽지 못해 근거 없이 내려줍니다.", exception);
      return null;
    }
  }

  /**
   * {@code metadata_json}에 넣을 문자열.
   *
   * <p>직렬화가 실패해도 답변은 살린다. 근거는 부가 정보이고, 이것 때문에 사용자가 받은 답을 잃을 이유가 없다.
   */
  public String toMetadataJson(
      List<SourceResponse> sources, List<String> suggestedContacts, long latencyMs) {
    try {
      return objectMapper.writeValueAsString(
          new ChatMessageMetadata(sources, suggestedContacts, latencyMs));
    } catch (JsonProcessingException exception) {
      log.warn("대화 메타데이터를 직렬화하지 못해 비워 둡니다.", exception);
      return null;
    }
  }
}
