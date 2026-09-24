package polight.server.domain.chat.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import polight.server.domain.chat.dto.ChatAnswerResponse.SourceResponse;
import polight.server.domain.chat.dto.ChatHistoryResponse.Message;
import polight.server.domain.chat.dto.RagQueryRequest.HistoryTurn;
import polight.server.domain.chat.dto.RagQueryResponse;
import polight.server.domain.chat.entity.ChatMessage;
import polight.server.domain.chat.entity.ChatSender;
import polight.server.domain.terms.entity.PolicyTerms;
import polight.server.domain.terms.entity.PolicyTermsChunk;
import polight.server.domain.terms.entity.TermsSource;
import polight.server.domain.terms.entity.TermsVerificationStatus;

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
  void keepsQuoteWhenChunkIsOutOfScope() {
    UUID chunkId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    RagQueryResponse response =
        new RagQueryResponse(
            "보상됩니다.", "TEXT", List.of(), List.of(legacySource(chunkId, documentId)));

    List<SourceResponse> sources = mapper.toSources(response, List.of());

    // 청크를 못 찾았거나 질의에 지목한 약관 밖의 것이면 위치만 비운다. 인용문은 AI 응답에 이미 있으므로 버리지 않는다.
    assertThat(sources).hasSize(1);
    assertThat(sources.get(0).quote()).isEqualTo("항공기 지연으로 인하여…");
    assertThat(sources.get(0).sectionTitle()).isNull();
    assertThat(sources.get(0).clausePath()).isNull();
    assertThat(sources.get(0).text()).isNull();
    // pageStart/pageEnd 가 없으면 page 하나로 둘 다 채운다.
    assertThat(sources.get(0).pageStart()).isEqualTo(12);
    assertThat(sources.get(0).pageEnd()).isEqualTo(12);
  }

  @Test
  void fallsBackToAiFieldsWhenChunkIsOutOfScope() {
    UUID chunkId = UUID.randomUUID();
    RagQueryResponse response =
        new RagQueryResponse(
            "보상됩니다.", "TEXT", List.of(), List.of(fullSource(chunkId, UUID.randomUUID())));

    List<SourceResponse> sources = mapper.toSources(response, List.of());

    // 청크를 못 찾아도 AI가 같은 값을 함께 보내므로 조항 제목도 원문도 없이 인용문만 뜨지는 않는다.
    assertThat(sources.get(0).sectionTitle()).isEqualTo("AI 휴대품손해 특별약관");
    assertThat(sources.get(0).text()).isEqualTo("AI 가 보낸 조항 원문 전체");
    assertThat(sources.get(0).clauseType()).isEqualTo("EXCLUSION");
    assertThat(sources.get(0).pageStart()).isEqualTo(50);
    assertThat(sources.get(0).pageEnd()).isEqualTo(52);
    // clausePath 는 AI가 보내지 않는다. 여전히 빈다.
    assertThat(sources.get(0).clausePath()).isNull();
  }

  @Test
  void fillsClauseLocationFromTermsChunk() {
    UUID chunkId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    RagQueryResponse response =
        new RagQueryResponse(
            "보상됩니다.", "TEXT", List.of(), List.of(legacySource(chunkId, documentId)));

    List<SourceResponse> sources = mapper.toSources(response, List.of(termsChunk(chunkId)));

    // 조항 제목·경로·페이지는 policy_terms_chunks 에 있다.
    assertThat(sources).hasSize(1);
    assertThat(sources.get(0).chunkId()).isEqualTo(chunkId);
    assertThat(sources.get(0).sectionTitle()).isEqualTo("제3관 배상책임 특별약관");
    assertThat(sources.get(0).clausePath()).isEqualTo("제3관 > 제12조");
    assertThat(sources.get(0).pageStart()).isEqualTo(11);
    assertThat(sources.get(0).pageEnd()).isEqualTo(13);
    // 약관 청크에는 문서가 없다. AI가 보낸 값을 그대로 흘린다.
    assertThat(sources.get(0).documentId()).isEqualTo(documentId);
  }

  @Test
  void fillsFullClauseTextFromTermsChunk() {
    UUID chunkId = UUID.randomUUID();
    RagQueryResponse response =
        new RagQueryResponse(
            "보상됩니다.", "TEXT", List.of(), List.of(legacySource(chunkId, UUID.randomUUID())));

    List<SourceResponse> sources = mapper.toSources(response, List.of(termsChunk(chunkId)));

    // quote 는 발췌라 잘려 있다. 약관 원문 화면에는 청크 본문 전체가 필요하다.
    assertThat(sources.get(0).quote()).isEqualTo("항공기 지연으로 인하여…");
    assertThat(sources.get(0).text()).isEqualTo("항공기 지연으로 인하여 발생한 비용을 보상합니다.");
    assertThat(sources.get(0).clauseType()).isEqualTo("GENERAL");
  }

  @Test
  void prefersTermsChunkOverAiForClauseFields() {
    UUID chunkId = UUID.randomUUID();
    RagQueryResponse response =
        new RagQueryResponse(
            "보상됩니다.", "TEXT", List.of(), List.of(fullSource(chunkId, UUID.randomUUID())));

    List<SourceResponse> sources = mapper.toSources(response, List.of(termsChunk(chunkId)));

    // 질의에 지목한 약관에 속한 것으로 확인된 청크라 어느 약관의 본문인지가 보장된다.
    // AI가 보낸 같은 이름의 값은 그 보장이 없으므로 청크가 있으면 쓰지 않는다.
    assertThat(sources.get(0).sectionTitle()).isEqualTo("제3관 배상책임 특별약관");
    assertThat(sources.get(0).text()).isEqualTo("항공기 지연으로 인하여 발생한 비용을 보상합니다.");
    assertThat(sources.get(0).clauseType()).isEqualTo("GENERAL");
    assertThat(sources.get(0).pageStart()).isEqualTo(11);
    assertThat(sources.get(0).pageEnd()).isEqualTo(13);
  }

  @Test
  void passesAnswerOnlyFieldsThroughFromAi() {
    UUID chunkId = UUID.randomUUID();
    RagQueryResponse response =
        new RagQueryResponse(
            "보상됩니다.", "TEXT", List.of(), List.of(fullSource(chunkId, UUID.randomUUID())));

    List<SourceResponse> sources = mapper.toSources(response, List.of(termsChunk(chunkId)));

    // index 와 cited 는 이 답변에서만 뜻이 있는 값이라 청크에 없다. 청크를 찾아도 AI 것을 쓴다.
    // chunk.chunkIndex(0) 는 "약관 안에서 몇 번째 청크인가"라서 뜻이 다르다.
    assertThat(sources.get(0).index()).isEqualTo(3);
    assertThat(sources.get(0).cited()).isTrue();
  }

  @Test
  void passesSuggestedContactsThrough() {
    RagQueryResponse response =
        new RagQueryResponse("경찰에 신고하세요.", "TEXT", List.of("POLICE", "EMBASSY"), List.of());

    assertThat(mapper.toSuggestedContacts(response)).containsExactly("POLICE", "EMBASSY");
  }

  @Test
  void passesUnknownContactKindThroughInsteadOfDroppingIt() {
    RagQueryResponse response =
        new RagQueryResponse("구급차를 부르세요.", "TEXT", List.of("AMBULANCE"), List.of());

    // 여기서 막으면 AI가 4번째 종류를 배포하는 순간 백엔드도 같이 배포해야 그 값이 화면에 닿는다.
    // 프론트가 모르는 값은 그리지 않을 뿐이다.
    assertThat(mapper.toSuggestedContacts(response)).containsExactly("AMBULANCE");
  }

  @Test
  void turnsOmittedSuggestedContactsIntoEmptyList() {
    RagQueryResponse response = new RagQueryResponse("보상됩니다.", "TEXT", null, List.of());

    // 프론트가 null 과 빈 배열을 나눠 다룰 이유가 없다.
    assertThat(mapper.toSuggestedContacts(response)).isEmpty();
  }

  @Test
  void readsStoredSuggestedContactsBackFromMetadata() {
    String metadata = mapper.toMetadataJson(List.of(), List.of("POLICE"), 900L);

    List<Message> messages = mapper.toMessages(List.of(assistantWithMetadata(metadata)));

    // 저장하지 않으면 앱을 껐다 켠 뒤 그 대화만 연락처가 사라진다.
    assertThat(messages.get(0).suggestedContacts()).containsExactly("POLICE");
  }

  @Test
  void returnsEmptyContactsForMessagesStoredBeforeTheFieldExisted() {
    // 필드가 생기기 전에 저장된 metadata_json 에는 이 키가 없다.
    List<Message> messages =
        mapper.toMessages(List.of(assistantWithMetadata("{\"sources\":[],\"latencyMs\":12}")));

    assertThat(messages.get(0).suggestedContacts()).isEmpty();
  }

  @Test
  void returnsEmptySourcesWhenAiOmitsThem() {
    assertThat(mapper.toSources(new RagQueryResponse("모르겠습니다.", "TEXT", null, null), List.of()))
        .isEmpty();
  }

  @Test
  void serializesMetadataWithLatency() {
    String json =
        mapper.toMetadataJson(
            List.of(new SourceResponse(null, null, null, "제3관", "제3관 > 제12조", 12, 12, null, "인용", null, null)),
            List.of("POLICE"),
            1234L);

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
    assertThat(failing.toMetadataJson(List.of(), List.of(), 1L)).isNull();
  }

  @Test
  void turnsStoredMessagesIntoOldestFirstView() {
    List<ChatMessage> recentFirst =
        List.of(
            message(ChatSender.ASSISTANT, "4시간 이상 지연 시 보상됩니다."),
            message(ChatSender.USER, "항공편 지연되면 보상돼요?"));

    List<Message> messages = mapper.toMessages(recentFirst);

    // 화면에 위에서 아래로 그리는 순서다.
    assertThat(messages)
        .extracting(Message::sender, Message::content)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(ChatSender.USER, "항공편 지연되면 보상돼요?"),
            org.assertj.core.groups.Tuple.tuple(ChatSender.ASSISTANT, "4시간 이상 지연 시 보상됩니다."));
  }

  @Test
  void readsStoredSourcesBackFromMetadata() {
    String metadata =
        mapper.toMetadataJson(
            List.of(new SourceResponse(null, null, null, "제3관", "제3관 > 제12조", 12, 12, null, "인용", null, null)),
            List.of("POLICE"),
            900L);

    List<Message> messages = mapper.toMessages(List.of(assistantWithMetadata(metadata)));

    assertThat(messages.get(0).sources()).hasSize(1);
    assertThat(messages.get(0).sources().get(0).clausePath()).isEqualTo("제3관 > 제12조");
  }

  @Test
  void returnsEmptySourcesWhenMetadataIsUnreadable() {
    List<Message> messages = mapper.toMessages(List.of(assistantWithMetadata("{깨진 JSON")));

    // 근거를 못 읽는다고 대화 이력 전체가 열리지 않으면 사용자는 자기 대화를 볼 수 없게 된다.
    assertThat(messages).hasSize(1);
    assertThat(messages.get(0).sources()).isEmpty();
  }

  @Test
  void returnsEmptySourcesWhenMetadataIsAbsent() {
    assertThat(mapper.toMessages(List.of(message(ChatSender.USER, "질문"))).get(0).sources())
        .isEmpty();
  }

  private ChatMessage assistantWithMetadata(String metadataJson) {
    return ChatMessage.builder()
        .sender(ChatSender.ASSISTANT)
        .content("보상됩니다.")
        .metadataJson(metadataJson)
        .build();
  }

  /** AI가 새 필드를 배포하기 전 모양. page 와 quote 만 온다. */
  private RagQueryResponse.Source legacySource(UUID chunkId, UUID documentId) {
    return new RagQueryResponse.Source(
        chunkId, documentId, null, null, 12, null, null, null, null, "항공기 지연으로 인하여…", null);
  }

  /** AI가 새 필드까지 채워 보낸 모양. 청크에 있는 값은 일부러 다르게 둔다. */
  private RagQueryResponse.Source fullSource(UUID chunkId, UUID documentId) {
    return new RagQueryResponse.Source(
        chunkId,
        documentId,
        3,
        "AI 휴대품손해 특별약관",
        51,
        50,
        52,
        "EXCLUSION",
        "AI 가 보낸 조항 원문 전체",
        "항공기 지연으로 인하여…",
        true);
  }

  private PolicyTermsChunk termsChunk(UUID chunkId) {
    PolicyTerms terms =
        PolicyTerms.builder()
            .insurerName("삼성화재해상보험")
            .productName("해외여행보험")
            .verificationStatus(TermsVerificationStatus.VERIFIED)
            .source(TermsSource.OFFICIAL)
            .build();

    PolicyTermsChunk chunk =
        PolicyTermsChunk.builder()
            .terms(terms)
            .chunkIndex(0)
            .pageStart(11)
            .pageEnd(13)
            .sectionTitle("제3관 배상책임 특별약관")
            .clausePath("제3관 > 제12조")
            .content("항공기 지연으로 인하여 발생한 비용을 보상합니다.")
            .build();
    // id 는 DB가 채운다. 매퍼가 id 로 짝을 맞추므로 테스트에서는 직접 넣는다.
    ReflectionTestUtils.setField(chunk, "id", chunkId);

    return chunk;
  }

  private ChatMessage message(ChatSender sender, String content) {
    return ChatMessage.builder().sender(sender).content(content).build();
  }
}
