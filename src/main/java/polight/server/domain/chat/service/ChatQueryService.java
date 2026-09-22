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
import polight.server.domain.terms.entity.PolicyTermsChunk;
import polight.server.domain.terms.service.TermsChunkQueryService;

/**
 * 질문 하나를 답변까지 끌고 간다.
 *
 * <p>이 클래스에 {@code @Transactional}이 없는 것이 의도다. LLM 호출은 수 초가 걸리는데, 트랜잭션 안에서 부르면 그 시간 동안 DB 커넥션을 쥐고
 * 있게 된다. 동시 질문이 커넥션 풀 크기를 넘기는 순간 관련 없는 요청까지 전부 대기한다. 그래서 DB 작업은 트랜잭션을 가진 다른 서비스에 맡기고, 이 클래스는 순서만
 * 관리한다.
 *
 * <pre>
 *   [트랜잭션] 세션 확보 → 이력 조회 → 질문 저장 → 검색 범위·담보 조회
 *   [트랜잭션 밖] AI 질의
 *   [트랜잭션] 근거 보강 → 답변 저장
 * </pre>
 *
 * <p>검색할 약관이 정해지지 않으면 AI를 부르지 않는다. 이유는 {@link #NO_TERMS_ANSWER} 쪽에 적어 두었다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatQueryService {

  /**
   * 검색할 약관이 없을 때 돌려주는 답변.
   *
   * <p>이 경우 AI를 부르지 않는다. 약관을 지목하지 않고 검색하면 그 사용자가 사지도 않은 상품의 조항이 걸려 "보상됩니다"라는 답이 나가고, 사용자는
   * 그 말을 믿고 보험금을 청구하지 않거나 진료를 받는다. 근거가 없다고 말하는 편이 낫다.
   */
  static final String NO_TERMS_ANSWER =
      "가입하신 증권에 연결된 약관을 찾지 못해 약관 근거를 확인할 수 없습니다. 증권을 등록했는지 확인해 주세요.";

  private final ChatSessionService chatSessionService;
  private final ChatMessageService chatMessageService;
  private final CertificateContextProvider certificateContextProvider;
  private final TermsChunkQueryService termsChunkQueryService;
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
    if (!certificate.hasTerms()) {
      return answerWithoutTerms(session, userId, tripId);
    }

    long startedAt = System.nanoTime();
    RagQueryResponse answer =
        ragQueryClient.query(
            new RagQueryRequest(
                userId,
                tripId,
                // 증권 분석이 매칭해 둔 약관. 여기서만 검색한다.
                certificate.termsId(),
                // 챗봇 화면에 문서를 고르는 UI가 없다. null 을 보내면 AI 가 약관 전체에서 검색한다.
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

    List<SourceResponse> sources = enrichSources(certificate.termsId(), answer);
    ChatMessage saved =
        chatMessageService.appendAssistantMessage(
            sessionId, answer.answer(), chatMessageMapper.toMetadataJson(sources, latencyMs));

    return new ChatAnswerResponse(
        sessionId, saved.getId(), saved.getContent(), saved.getResponseType(), sources);
  }

  /**
   * 약관 없이 답한다. AI 호출도, 근거 조회도 하지 않는다.
   *
   * <p>질문은 이미 저장되어 있다. 답변도 남겨야 이력이 질문만 홀로 있는 상태가 되지 않는다. 지연 시간 메타데이터는 붙이지 않는다 -- 재지 않은 값을
   * 0으로 적어 두면 나중에 "AI가 즉답했다"로 읽힌다.
   */
  private ChatAnswerResponse answerWithoutTerms(ChatSession session, UUID userId, UUID tripId) {
    UUID sessionId = session.getId();
    // 이 로그가 잦으면 약관 적재나 증권-약관 매칭이 밀린 것이다. 챗봇이 아니라 그쪽을 봐야 한다.
    log.info("연결된 약관이 없어 AI 질의를 건너뜁니다: userId={}, tripId={}", userId, tripId);

    ChatMessage saved = chatMessageService.appendAssistantMessage(sessionId, NO_TERMS_ANSWER, null);

    return new ChatAnswerResponse(
        sessionId, saved.getId(), saved.getContent(), saved.getResponseType(), List.of());
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

  /**
   * AI가 돌려준 근거에 조항 위치를 채운다.
   *
   * <p>조회 범위는 질의에 실어 보낸 약관({@code termsId})이다. 검색 범위로 그 약관 하나를 지목했으므로 돌아온 청크도 그 약관의 것이어야 한다.
   * AI 응답을 그대로 믿고 조회하면 다른 약관의 조항 제목이 이 답변의 근거로 실린다.
   *
   * <p>사용자 소유로 거르지 않는 이유: 약관은 상품 공용 문서라 청크에 주인이 없다. "이 사용자가 이 약관을 볼 수 있는가"는 증권-약관 매칭이
   * 이미 판정했고, 그 결과가 여기 들어온 {@code termsId}다.
   */
  private List<SourceResponse> enrichSources(UUID termsId, RagQueryResponse answer) {
    if (answer.sources() == null || answer.sources().isEmpty()) {
      return List.of();
    }

    List<UUID> chunkIds =
        answer.sources().stream()
            .map(RagQueryResponse.Source::chunkId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();

    List<PolicyTermsChunk> scopedChunks = termsChunkQueryService.findChunksIn(termsId, chunkIds);
    warnIfNoChunkResolved(termsId, chunkIds, scopedChunks);

    return chatMessageMapper.toSources(answer, scopedChunks);
  }

  /**
   * AI가 근거를 보냈는데 한 건도 찾지 못하면 남긴다.
   *
   * <p>이건 계약이 어긋났다는 신호다. 질의에 {@code termsId}를 실어 보냈으니 AI가 그 약관의 {@code policy_terms_chunks}에서
   * 검색했다면 id가 전부 여기서 찾아진다. 하나도 못 찾는 것은 AI가 다른 테이블({@code policy_chunks})의 id를 돌려주고 있거나, 우리가
   * 지목한 약관이 아닌 곳에서 검색했다는 뜻이다.
   *
   * <p>화면에서는 "인용문은 있는데 조항이 안 보인다"로만 드러나 원인을 짚기 어렵다. 그래서 서버 쪽에 흔적을 남긴다.
   *
   * <p>답변은 그대로 내려간다. 비는 것은 조항 위치뿐이고 인용문은 AI 응답에 이미 들어 있어, 이것 때문에 답을 막을 이유가 없다.
   */
  private void warnIfNoChunkResolved(
      UUID termsId, List<UUID> chunkIds, List<PolicyTermsChunk> scopedChunks) {
    if (chunkIds.isEmpty() || !scopedChunks.isEmpty()) {
      return;
    }

    log.warn(
        "AI가 돌려준 근거 청크를 약관에서 하나도 찾지 못했습니다(조항 위치 없이 인용문만 내려갑니다): "
            + "termsId={}, chunkIds={}건",
        termsId,
        chunkIds.size());
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
