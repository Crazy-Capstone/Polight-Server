package polight.server.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import polight.server.domain.chat.client.RagQueryClient;
import polight.server.domain.chat.dto.ChatAnswerResponse;
import polight.server.domain.chat.dto.ChatQuestionRequest;
import polight.server.domain.chat.dto.RagQueryRequest;
import polight.server.domain.chat.dto.RagQueryResponse;
import polight.server.domain.chat.entity.ChatMessage;
import polight.server.domain.chat.entity.ChatResponseType;
import polight.server.domain.chat.entity.ChatSender;
import polight.server.domain.chat.entity.ChatSession;
import polight.server.domain.chat.mapper.ChatMessageMapper;
import polight.server.domain.chat.service.CertificateContextProvider.CertificateContext;
import polight.server.domain.terms.service.TermsChunkQueryService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatQueryServiceTest {

  @Mock private ChatSessionService chatSessionService;
  @Mock private ChatMessageService chatMessageService;
  @Mock private CertificateContextProvider certificateContextProvider;
  @Mock private TermsChunkQueryService termsChunkQueryService;
  @Mock private RagQueryClient ragQueryClient;
  @Mock private ChatMessageMapper chatMessageMapper;

  @Mock private ChatSession session;

  private ChatQueryService service;

  private final UUID userId = UUID.randomUUID();
  private final UUID tripId = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();
  private final UUID termsId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new ChatQueryService(
            chatSessionService,
            chatMessageService,
            certificateContextProvider,
            termsChunkQueryService,
            ragQueryClient,
            chatMessageMapper);

    given(session.getId()).willReturn(sessionId);
    given(chatSessionService.getOrCreateSession(userId, tripId)).willReturn(session);
    given(chatMessageService.loadRecentHistory(sessionId)).willReturn(List.of());
    given(chatMessageMapper.toHistory(anyList())).willReturn(List.of());
    given(certificateContextProvider.load(userId, tripId))
        .willReturn(new CertificateContext(termsId, List.of(), false));
    given(ragQueryClient.query(any())).willReturn(answer("보상됩니다."));
    given(chatMessageMapper.toSources(any(), anyList())).willReturn(List.of());
    given(chatMessageMapper.toSuggestedContacts(any())).willReturn(List.of());
    given(chatMessageService.appendAssistantMessage(eq(sessionId), any(), any()))
        .willReturn(assistantMessage("보상됩니다."));
  }

  @Test
  void savesQuestionBeforeCallingAiServer() {
    service.ask(userId, tripId, new ChatQuestionRequest("항공편 지연되면 보상돼요?"));

    // LLM이 실패해도 사용자가 보낸 질문은 남아 있어야 한다. 순서가 뒤바뀌면 화면에서
    // 방금 보낸 메시지가 사라진다.
    InOrder order = inOrder(chatMessageService, ragQueryClient);
    order.verify(chatMessageService).appendUserMessage(sessionId, "항공편 지연되면 보상돼요?");
    order.verify(ragQueryClient).query(any());
  }

  @Test
  void readsHistoryBeforeSavingQuestion() {
    service.ask(userId, tripId, new ChatQuestionRequest("그럼 얼마까지요?"));

    // 저장 후에 읽으면 방금 넣은 질문이 이력에 섞여 같은 문장이 프롬프트에 두 번 실린다.
    InOrder order = inOrder(chatMessageService);
    order.verify(chatMessageService).loadRecentHistory(sessionId);
    order.verify(chatMessageService).appendUserMessage(eq(sessionId), any());
  }

  @Test
  void sendsTripScopedRequestWithCertificateContext() {
    given(certificateContextProvider.load(userId, tripId))
        .willReturn(
            new CertificateContext(
                termsId,
                List.of(new RagQueryRequest.Coverage(
                        "해외의료비", true, 30_000_000L, "KRW", "* 자기부담금 10,000")),
                false));

    service.ask(userId, tripId, new ChatQuestionRequest("  병원비 얼마까지요?  "));

    ArgumentCaptor<RagQueryRequest> captor = ArgumentCaptor.forClass(RagQueryRequest.class);
    verify(ragQueryClient).query(captor.capture());
    RagQueryRequest sent = captor.getValue();

    assertThat(sent.userId()).isEqualTo(userId);
    assertThat(sent.tripId()).isEqualTo(tripId);
    assertThat(sent.sessionId()).isEqualTo(sessionId);
    // 증권 분석이 매칭해 둔 약관에서만 검색한다.
    assertThat(sent.termsId()).isEqualTo(termsId);
    assertThat(sent.question()).isEqualTo("병원비 얼마까지요?");
    // 챗봇 화면에 문서를 고르는 UI가 없다. null 이면 AI 가 여행 단위로 검색한다.
    assertThat(sent.documentId()).isNull();
    // policies 행을 만드는 경로가 없어 항상 null 이다.
    assertThat(sent.policyId()).isNull();
    // 특약명 필터는 검색 품질 개선 효과가 확인되지 않아 보내지 않는다.
    assertThat(sent.clausePaths()).isEmpty();
    assertThat(sent.coverages()).hasSize(1);
    assertThat(sent.coverages().get(0).name()).isEqualTo("해외의료비");
    // 자기부담금·물품당 한도는 증권에만 있다. 이게 빠지면 그 질문에 답할 수 없다.
    assertThat(sent.coverages().get(0).conditions()).isEqualTo("* 자기부담금 10,000");
    assertThat(sent.coveragesComplete()).isFalse();
  }

  @Test
  void skipsAiCallAndAnswersWithoutGroundsWhenNoTermsLinked() {
    given(certificateContextProvider.load(userId, tripId))
        .willReturn(new CertificateContext(null, List.of(), false));
    given(chatMessageService.appendAssistantMessage(sessionId, ChatQueryService.NO_TERMS_ANSWER, null))
        .willReturn(assistantMessage(ChatQueryService.NO_TERMS_ANSWER));

    ChatAnswerResponse response = service.ask(userId, tripId, new ChatQuestionRequest("병원비 나와요?"));

    // 약관을 지목하지 않고 검색하면 사지도 않은 상품의 조항으로 "보상됩니다"가 나간다.
    verify(ragQueryClient, org.mockito.Mockito.never()).query(any());
    assertThat(response.answer()).isEqualTo(ChatQueryService.NO_TERMS_ANSWER);
    assertThat(response.sources()).isEmpty();
  }

  @Test
  void stillSavesQuestionWhenNoTermsLinked() {
    given(certificateContextProvider.load(userId, tripId))
        .willReturn(new CertificateContext(null, List.of(), false));
    given(chatMessageService.appendAssistantMessage(sessionId, ChatQueryService.NO_TERMS_ANSWER, null))
        .willReturn(assistantMessage(ChatQueryService.NO_TERMS_ANSWER));

    service.ask(userId, tripId, new ChatQuestionRequest("병원비 나와요?"));

    // 질문만 사라지면 화면에서 방금 보낸 메시지가 없어진 것처럼 보인다.
    verify(chatMessageService).appendUserMessage(sessionId, "병원비 나와요?");
  }

  @Test
  void storesAnswerAsTextEvenWhenAiSendsCardType() {
    given(ragQueryClient.query(any()))
        .willReturn(new RagQueryResponse("병원 목록입니다.", "HOSPITAL_CARDS", List.of(), List.of()));

    ChatAnswerResponse response = service.ask(userId, tripId, new ChatQuestionRequest("병원 알려줘"));

    // 카드형은 렌더링에 필요한 데이터 출처가 아직 없다. 저장은 TEXT 하나로 고정한다.
    assertThat(response.responseType()).isEqualTo(ChatResponseType.TEXT);
  }

  @Test
  void reusesExistingSessionWhenCreationRaces() {
    given(chatSessionService.getOrCreateSession(userId, tripId))
        .willThrow(new DataIntegrityViolationException("uk_chat_sessions_user_trip"));
    given(chatSessionService.getSession(userId, tripId)).willReturn(session);

    ChatAnswerResponse response = service.ask(userId, tripId, new ChatQuestionRequest("보상돼요?"));

    // 유니크 인덱스가 막은 쪽은 다른 요청이 이미 만든 세션을 쓴다. 실패로 돌려주지 않는다.
    assertThat(response.sessionId()).isEqualTo(sessionId);
    verify(chatSessionService).getSession(userId, tripId);
  }

  @Test
  void doesNotLookUpChunksWhenAiReturnsNoSources() {
    given(ragQueryClient.query(any())).willReturn(new RagQueryResponse("모르겠습니다.", "TEXT", null, null));

    service.ask(userId, tripId, new ChatQuestionRequest("이건 뭐죠?"));

    verify(termsChunkQueryService, org.mockito.Mockito.never()).findChunksIn(any(), anyList());
    verify(chatMessageService).appendAssistantMessage(eq(sessionId), eq("모르겠습니다."), any());
  }

  @Test
  void looksUpSourceChunksInTheTermsItSearched() {
    UUID chunkId = UUID.randomUUID();
    given(ragQueryClient.query(any()))
        .willReturn(
            new RagQueryResponse(
                "보상됩니다.",
                "TEXT",
                List.of(),
                List.of(new RagQueryResponse.Source(chunkId, UUID.randomUUID(), 12, "인용"))));
    given(termsChunkQueryService.findChunksIn(eq(termsId), anyList())).willReturn(List.of());

    service.ask(userId, tripId, new ChatQuestionRequest("보상돼요?"));

    // 검색 범위로 이 약관 하나를 지목했으므로 돌아온 청크도 그 약관의 것이어야 한다.
    // 범위 없이 조회하면 다른 약관의 조항 제목이 이 답변의 근거로 실린다.
    verify(termsChunkQueryService).findChunksIn(termsId, List.of(chunkId));
  }

  @Test
  void stillAnswersWhenNoSourceChunkIsFound() {
    given(ragQueryClient.query(any()))
        .willReturn(
            new RagQueryResponse(
                "보상됩니다.",
                "TEXT",
                List.of(),
                List.of(new RagQueryResponse.Source(UUID.randomUUID(), null, 12, "인용"))));
    given(termsChunkQueryService.findChunksIn(any(), anyList())).willReturn(List.of());

    ChatAnswerResponse response = service.ask(userId, tripId, new ChatQuestionRequest("보상돼요?"));

    // 비는 것은 조항 위치뿐이고 인용문은 AI 응답에 이미 들어 있다. 답을 막을 이유가 없다.
    assertThat(response.answer()).isEqualTo("보상됩니다.");
    verify(chatMessageService).appendAssistantMessage(eq(sessionId), eq("보상됩니다."), any());
  }

  @Test
  void passesSuggestedContactsFromAiToTheResponse() {
    given(ragQueryClient.query(any()))
        .willReturn(new RagQueryResponse("경찰에 신고하세요.", "TEXT", List.of("POLICE"), List.of()));
    given(chatMessageMapper.toSuggestedContacts(any())).willReturn(List.of("POLICE"));
    given(chatMessageService.appendAssistantMessage(eq(sessionId), any(), any()))
        .willReturn(assistantMessage("경찰에 신고하세요."));

    ChatAnswerResponse response = service.ask(userId, tripId, new ChatQuestionRequest("도난당했어요"));

    assertThat(response.suggestedContacts()).containsExactly("POLICE");
    // 이력 조회도 같은 모양이어야 하므로 metadata_json 에 함께 저장한다.
    verify(chatMessageMapper).toMetadataJson(anyList(), eq(List.of("POLICE")), anyLong());
  }

  @Test
  void sendsNoContactsWhenAiCallIsSkipped() {
    given(certificateContextProvider.load(userId, tripId))
        .willReturn(new CertificateContext(null, List.of(), false));
    given(chatMessageService.appendAssistantMessage(sessionId, ChatQueryService.NO_TERMS_ANSWER, null))
        .willReturn(assistantMessage(ChatQueryService.NO_TERMS_ANSWER));

    ChatAnswerResponse response = service.ask(userId, tripId, new ChatQuestionRequest("도난당했어요"));

    // 사고 정황인지 판단하는 주체는 답변 LLM인데 그 호출을 건너뛰었다.
    assertThat(response.suggestedContacts()).isEmpty();
  }

  @Test
  void measuresAiLatencyForMetadata() {
    service.ask(userId, tripId, new ChatQuestionRequest("보상돼요?"));

    // 답변이 느릴 때 검색·생성 어느 쪽 문제인지 나누려면 저장해 둔 값이 있어야 한다.
    verify(chatMessageMapper).toMetadataJson(anyList(), anyList(), anyLong());
  }

  private RagQueryResponse answer(String text) {
    return new RagQueryResponse(text, "TEXT", List.of(), List.of());
  }

  private ChatMessage assistantMessage(String content) {
    return ChatMessage.builder()
        .session(session)
        .sender(ChatSender.ASSISTANT)
        .content(content)
        .responseType(ChatResponseType.TEXT)
        .build();
  }
}
