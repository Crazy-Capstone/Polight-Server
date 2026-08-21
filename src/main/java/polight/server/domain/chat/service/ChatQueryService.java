package polight.server.domain.chat.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import polight.server.domain.chat.client.RagQueryClient;
import polight.server.domain.chat.dto.ChatAnswerResponse;
import polight.server.domain.chat.dto.ChatAnswerResponse.SourceResponse;
import polight.server.domain.chat.dto.ChatQuestionRequest;
import polight.server.domain.chat.dto.RagQueryRequest;
import polight.server.domain.chat.dto.RagQueryResponse;
import polight.server.domain.chat.entity.ChatMessage;
import polight.server.domain.chat.entity.ChatResponseType;
import polight.server.domain.chat.entity.ChatSession;
import polight.server.domain.chat.mapper.ChatMessageMapper;
import polight.server.domain.chat.service.CertificateContextProvider.CertificateContext;
import polight.server.domain.rag.entity.PolicyChunk;
import polight.server.domain.rag.service.RagSearchScopeService;

/**
 * 질문 하나를 답변까지 끌고 간다.
 *
 * <p>이 클래스에 {@code @Transactional}이 없는 것이 의도다. LLM 호출은 수 초가 걸리는데, 트랜잭션 안에서 부르면 그 시간 동안 DB 커넥션을 쥐고
 * 있게 된다. 동시 질문이 커넥션 풀 크기를 넘기는 순간 관련 없는 요청까지 전부 대기한다. 그래서 DB 작업은 트랜잭션을 가진 다른 서비스에 맡기고, 이 클래스는 순서만
 * 관리한다.
 *
 * <pre>
 *   [트랜잭션] 세션 확보 → 이력 조회 → 질문 저장 → 담보 조회
 *   [트랜잭션 밖] AI 질의
 *   [트랜잭션] 근거 보강 → 답변 저장
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatQueryService {

  private final ChatSessionService chatSessionService;
  private final ChatMessageService chatMessageService;
  private final CertificateContextProvider certificateContextProvider;
  private final RagSearchScopeService ragSearchScopeService;
  private final RagQueryClient ragQueryClient;
  private final ChatMessageMapper chatMessageMapper;

  public ChatAnswerResponse ask(UUID userId, UUID tripId, ChatQuestionRequest request) {
    String question = request.question().trim();

    ChatSession session = resolveSession(userId, tripId);
    UUID sessionId = session.getId();

    // 이력을 먼저 읽고 질문을 저장한다. 순서가 뒤바뀌면 방금 넣은 질문이 이력에도 들어가
    // 같은 문장이 프롬프트에 두 번 실린다.
    List<RagQueryRequest.HistoryTurn> history =
        chatMessageMapper.toHistory(chatMessageService.loadRecentHistory(sessionId));
    chatMessageService.appendUserMessage(sessionId, question);

    CertificateContext certificate = certificateContextProvider.load(userId, tripId);

    long startedAt = System.nanoTime();
    RagQueryResponse answer =
        ragQueryClient.query(
            new RagQueryRequest(
                userId,
                tripId,
                // 챗봇 화면에 문서를 고르는 UI가 없다. null 을 보내면 AI 가 여행 단위로 검색한다.
                null,
                // policies 행을 만드는 경로가 없어 항상 null 이다. AI 도 스코프로 쓰지 않는다.
                null,
                sessionId,
                question,
                history,
                certificate.coverages(),
                certificate.complete(),
                // 특약명 필터는 AI 쪽 실측에서 효과가 확인되지 않아 보내지 않는다.
                List.of()));
    long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;

    warnIfUnsupportedResponseType(answer, sessionId);

    List<SourceResponse> sources = enrichSources(userId, answer);
    ChatMessage saved =
        chatMessageService.appendAssistantMessage(
            sessionId, answer.answer(), chatMessageMapper.toMetadataJson(sources, latencyMs));

    return new ChatAnswerResponse(
        sessionId, saved.getId(), saved.getContent(), saved.getResponseType(), sources);
  }

  /**
   * 여행의 세션을 확보한다.
   *
   * <p>같은 여행에 첫 질문이 동시에 들어오면 유니크 인덱스(V7)가 한쪽을 막는다. 막힌 쪽은 다른 요청이 이미 만든 세션을 쓰면 되므로 실패로 돌려주지 않는다.
   */
  private ChatSession resolveSession(UUID userId, UUID tripId) {
    try {
      return chatSessionService.getOrCreateSession(userId, tripId);
    } catch (DataIntegrityViolationException duplicate) {
      log.info("세션 생성이 경합해 기존 세션을 사용합니다: tripId={}", tripId);
      return chatSessionService.getSession(userId, tripId);
    }
  }

  /** AI가 돌려준 근거에 조항 위치를 채운다. 청크는 사용자 소유인 것만 쓴다. */
  private List<SourceResponse> enrichSources(UUID userId, RagQueryResponse answer) {
    if (answer.sources() == null || answer.sources().isEmpty()) {
      return List.of();
    }

    List<UUID> chunkIds =
        answer.sources().stream()
            .map(RagQueryResponse.Source::chunkId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();

    List<PolicyChunk> ownedChunks = ragSearchScopeService.findOwnedChunks(userId, chunkIds);

    return chatMessageMapper.toSources(answer, ownedChunks);
  }

  /**
   * AI가 TEXT 외의 응답 유형을 보내면 로그만 남긴다.
   *
   * <p>카드형 4종({@code HOSPITAL_CARDS} 등)은 렌더링에 필요한 데이터 출처가 아직 없어 저장은 {@code TEXT}로 한다. 조용히 바꾸면 나중에
   * 카드가 안 나오는 이유를 찾기 어려우므로 흔적을 남긴다.
   */
  private void warnIfUnsupportedResponseType(RagQueryResponse answer, UUID sessionId) {
    String responseType = answer.responseType();
    if (responseType != null && !ChatResponseType.TEXT.name().equals(responseType)) {
      log.warn(
          "지원하지 않는 응답 유형을 TEXT로 저장합니다: sessionId={}, responseType={}", sessionId, responseType);
    }
  }
}
