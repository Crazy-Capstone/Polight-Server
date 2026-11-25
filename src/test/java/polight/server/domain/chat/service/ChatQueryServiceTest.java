package polight.server.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
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
import polight.server.domain.chat.client.UnregisteredChatClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import polight.server.domain.chat.dto.CertificateStatus;
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
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatQueryServiceTest {

  @Mock private ChatSessionService chatSessionService;
  @Mock private ChatMessageService chatMessageService;
  @Mock private CertificateContextProvider certificateContextProvider;
  @Mock private TermsChunkQueryService termsChunkQueryService;
  @Mock private RagQueryClient ragQueryClient;
  @Mock private ChatMessageMapper chatMessageMapper;
  @Mock private UnregisteredChatClient unregisteredChatClient;

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
            chatMessageMapper,
            unregisteredChatClient);

    given(session.getId()).willReturn(sessionId);
    given(chatSessionService.getOrCreateSession(userId, tripId)).willReturn(session);
    given(chatMessageService.loadRecentHistory(sessionId)).willReturn(List.of());
    given(chatMessageMapper.toHistory(anyList())).willReturn(List.of());
    given(certificateContextProvider.load(userId, tripId))
        .willReturn(new CertificateContext(termsId, List.of(), false, CertificateStatus.COMPLETED));
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
  void unregisteredRequestHasNoTripOrPolicyScopeAndReusesMessagePersistence() {
    given(chatSessionService.getOrCreateUnregisteredSession(userId)).willReturn(session);
    given(unregisteredChatClient.query(any())).willReturn(answer("General guidance"));
    given(chatMessageService.appendAssistantMessage(eq(sessionId), any(), any()))
        .willReturn(assistantMessage("General guidance"));

    var response = service.askUnregistered(userId, new ChatQuestionRequest("  Help  "));
    var captor = ArgumentCaptor.forClass(RagQueryRequest.class);
    verify(unregisteredChatClient).query(captor.capture());
    var sent = captor.getValue();
    assertThat(sent.userId()).isEqualTo(userId);
    assertThat(sent.tripId()).isNull();
    assertThat(sent.termsId()).isNull();
    assertThat(sent.documentId()).isNull();
    assertThat(sent.policyId()).isNull();
    assertThat(sent.certificateStatus()).isEqualTo(CertificateStatus.UNREGISTERED);
    assertThat(sent.coverages()).isEmpty();
    assertThat(sent.coveragesComplete()).isFalse();
    assertThat(sent.question()).isEqualTo("Help");
    assertThat(response.answer()).isEqualTo("General guidance");
    verify(certificateContextProvider, org.mockito.Mockito.never()).load(any(), any());
    verify(termsChunkQueryService, org.mockito.Mockito.never()).findChunksIn(any(), anyList());
    verify(ragQueryClient, org.mockito.Mockito.never()).query(any());
    var order = inOrder(chatMessageService, unregisteredChatClient);
    order.verify(unregisteredChatClient).verifyAvailable();
    order.verify(chatMessageService).loadRecentHistory(sessionId);
    order.verify(chatMessageService).appendUserMessage(sessionId, "Help");
    order.verify(unregisteredChatClient).query(any());
  }

  @Test
  void registeredUserIsRejectedBeforeSavingQuestionOrCallingAi() {
    given(chatSessionService.getOrCreateUnregisteredSession(userId))
        .willThrow(new BaseException(ErrorCode.CHAT_TRIP_ALREADY_REGISTERED));
    assertThatExceptionOfType(BaseException.class)
        .isThrownBy(() -> service.askUnregistered(userId, new ChatQuestionRequest("Help")))
        .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CHAT_TRIP_ALREADY_REGISTERED));
    verify(chatMessageService, org.mockito.Mockito.never()).appendUserMessage(any(), any());
    verify(unregisteredChatClient, org.mockito.Mockito.never()).query(any());
  }

  @Test
  void disabledUnregisteredModeDoesNotCreateSessionsOrMessages() {
    org.mockito.Mockito.doThrow(new BaseException(ErrorCode.UNREGISTERED_CHAT_NOT_READY))
        .when(unregisteredChatClient).verifyAvailable();
    assertThatExceptionOfType(BaseException.class)
        .isThrownBy(() -> service.askUnregistered(userId, new ChatQuestionRequest("Help")))
        .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.UNREGISTERED_CHAT_NOT_READY));
    verify(chatSessionService, org.mockito.Mockito.never()).getOrCreateUnregisteredSession(any());
    verify(chatMessageService, org.mockito.Mockito.never()).appendUserMessage(any(), any());
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
                false, CertificateStatus.COMPLETED));

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

  @ParameterizedTest
  @EnumSource(CertificateStatus.class)
  void callsAiForEveryStateWithoutTermsAndDoesNotQueryChunks(CertificateStatus status) {
    given(certificateContextProvider.load(userId, tripId))
        .willReturn(new CertificateContext(null, List.of(), false, status));
    given(ragQueryClient.query(any())).willReturn(new RagQueryResponse(
        "State guidance", "TEXT", List.of("HOSPITAL"),
        List.of(source(UUID.randomUUID(), UUID.randomUUID())), List.of(), null, null));
    given(chatMessageService.appendAssistantMessage(eq(sessionId), any(), any()))
        .willReturn(assistantMessage("State guidance"));

    var response = service.ask(userId, tripId, new ChatQuestionRequest("Help"));
    var captor = ArgumentCaptor.forClass(RagQueryRequest.class);
    verify(ragQueryClient).query(captor.capture());

    assertThat(captor.getValue().certificateStatus()).isEqualTo(status);
    assertThat(captor.getValue().termsId()).isNull();
    assertThat(response.answer()).isEqualTo("State guidance");
    assertThat(response.sources()).isEmpty();
    verify(chatMessageService).appendUserMessage(sessionId, "Help");
    verify(termsChunkQueryService, org.mockito.Mockito.never()).findChunksIn(any(), anyList());
    verify(chatMessageMapper, org.mockito.Mockito.never()).toSources(any(), anyList());
  }

  @Test
  void storesAnswerAsTextEvenWhenAiSendsCardType() {
    given(ragQueryClient.query(any()))
        .willReturn(new RagQueryResponse("병원 목록입니다.", "HOSPITAL_CARDS", List.of(), List.of(), List.of(), null, null));

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
    given(ragQueryClient.query(any())).willReturn(new RagQueryResponse("모르겠습니다.", "TEXT", null, null, List.of(), null, null));

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
                List.of(source(chunkId, UUID.randomUUID())),
                List.of(), null, null));
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
                List.of(source(UUID.randomUUID(), null)),
                List.of(), null, null));
    given(termsChunkQueryService.findChunksIn(any(), anyList())).willReturn(List.of());

    ChatAnswerResponse response = service.ask(userId, tripId, new ChatQuestionRequest("보상돼요?"));

    // 비는 것은 조항 위치뿐이고 인용문은 AI 응답에 이미 들어 있다. 답을 막을 이유가 없다.
    assertThat(response.answer()).isEqualTo("보상됩니다.");
    verify(chatMessageService).appendAssistantMessage(eq(sessionId), eq("보상됩니다."), any());
  }

  @Test
  void passesSuggestedContactsFromAiToTheResponse() {
    given(ragQueryClient.query(any()))
        .willReturn(new RagQueryResponse("경찰에 신고하세요.", "TEXT", List.of("POLICE"), List.of(), List.of(), null, null));
    given(chatMessageMapper.toSuggestedContacts(any())).willReturn(List.of("POLICE"));
    given(chatMessageService.appendAssistantMessage(eq(sessionId), any(), any()))
        .willReturn(assistantMessage("경찰에 신고하세요."));

    ChatAnswerResponse response = service.ask(userId, tripId, new ChatQuestionRequest("도난당했어요"));

    assertThat(response.suggestedContacts()).containsExactly("POLICE");
    // 이력 조회도 같은 모양이어야 하므로 metadata_json 에 함께 저장한다.
    verify(chatMessageMapper).toMetadataJson(anyList(), eq(List.of("POLICE")), anyList(), nullable(String.class), nullable(String.class), anyLong());
  }

  @Test
  void measuresAiLatencyForMetadata() {
    service.ask(userId, tripId, new ChatQuestionRequest("보상돼요?"));

    // 답변이 느릴 때 검색·생성 어느 쪽 문제인지 나누려면 저장해 둔 값이 있어야 한다.
    verify(chatMessageMapper).toMetadataJson(anyList(), anyList(), anyList(), nullable(String.class), nullable(String.class), anyLong());
  }

  @Test
  void returnsAndStoresUnmodifiedClaimGuidance() {
    String notice = "  General guidance  ";
    String url = "https://example.com/claim-documents?lang=ko#documents";
    given(ragQueryClient.query(any())).willReturn(
        new RagQueryResponse("Answer", "TEXT", List.of(), List.of(), List.of(), notice, url));

    var response = service.ask(userId, tripId, new ChatQuestionRequest("Documents?"));

    assertThat(response.claimDocumentsNotice()).isEqualTo(notice);
    assertThat(response.claimDocumentsUrl()).isEqualTo(url);
    verify(chatMessageMapper).toMetadataJson(
        anyList(), anyList(), anyList(), eq(notice), eq(url), anyLong());
  }

  @Test
  void aiFailureKeepsQuestionAndDoesNotSaveAnInventedAnswer() {
    given(certificateContextProvider.load(userId, tripId))
        .willReturn(new CertificateContext(null, List.of(), false, CertificateStatus.ANALYZING));
    given(ragQueryClient.query(any())).willThrow(new BaseException(ErrorCode.AI_CHAT_REQUEST_FAILED));

    assertThatExceptionOfType(BaseException.class)
        .isThrownBy(() -> service.ask(userId, tripId, new ChatQuestionRequest("Help")))
        .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AI_CHAT_REQUEST_FAILED));
    verify(chatMessageService).appendUserMessage(sessionId, "Help");
    verify(chatMessageService, org.mockito.Mockito.never()).appendAssistantMessage(any(), any(), any());
  }

  private RagQueryResponse answer(String text) {
    return new RagQueryResponse(text, "TEXT", List.of(), List.of(), List.of(), null, null);
  }

  private RagQueryResponse.Source source(UUID chunkId, UUID documentId) {
    return new RagQueryResponse.Source(
        chunkId, documentId, 1, "휴대품손해 특별약관", 12, 12, 12, "COVERAGE", "조항 원문 전체", "인용", true);
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
