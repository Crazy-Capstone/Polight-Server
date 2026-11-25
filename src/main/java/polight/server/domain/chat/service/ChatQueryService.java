package polight.server.domain.chat.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import polight.server.domain.chat.client.RagQueryClient;
import polight.server.domain.chat.client.UnregisteredChatClient;
import polight.server.domain.chat.dto.CertificateStatus;
import polight.server.domain.chat.dto.ChatAnswerResponse;
import polight.server.domain.chat.dto.ChatAnswerResponse.SourceResponse;
import polight.server.domain.chat.dto.ChatQuestionRequest;
import polight.server.domain.chat.dto.ClaimDocument;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatQueryService {

  private final ChatSessionService chatSessionService;
  private final ChatMessageService chatMessageService;
  private final CertificateContextProvider certificateContextProvider;
  private final TermsChunkQueryService termsChunkQueryService;
  private final RagQueryClient ragQueryClient;
  private final ChatMessageMapper chatMessageMapper;
  private final UnregisteredChatClient unregisteredChatClient;

  public ChatAnswerResponse ask(UUID userId, UUID tripId, ChatQuestionRequest request) {
    return ask(userId, tripId, request, resolveSession(userId, tripId), false);
  }

  public ChatAnswerResponse askUnregistered(UUID userId, ChatQuestionRequest request) {
    unregisteredChatClient.verifyAvailable();
    return ask(userId, null, request, chatSessionService.getOrCreateUnregisteredSession(userId), true);
  }

  private ChatAnswerResponse ask(
      UUID userId, UUID tripId, ChatQuestionRequest request, ChatSession session, boolean unregistered) {
    String question = request.question().trim();

    UUID sessionId = session.getId();

    // 이력을 먼저 읽고 질문을 저장한다. 순서가 뒤바뀌면 방금 넣은 질문이 이력에도 들어가
    // 같은 문장이 프롬프트에 두 번 실린다.
    List<RagQueryRequest.HistoryTurn> history =
        chatMessageMapper.toHistory(chatMessageService.loadRecentHistory(sessionId));
    chatMessageService.appendUserMessage(sessionId, question);

    CertificateContext certificate = unregistered
        ? CertificateContext.empty(CertificateStatus.UNREGISTERED)
        : certificateContextProvider.load(userId, tripId);

    long startedAt = System.nanoTime();
    RagQueryRequest query = new RagQueryRequest(
        userId,
        tripId,
        // 증권 분석이 매칭해 둔 약관. 여기서만 검색한다.
        certificate.termsId(),
        null,
        // policies 행을 만드는 경로가 없어 항상 null 이다. AI 도 스코프로 쓰지 않는다.
        null,
        sessionId,
        question,
        history,
        certificate.coverages(),
        certificate.complete(),
        // 특약명 필터는 AI 쪽 실측에서 효과가 확인되지 않아 보내지 않는다.
        List.of(),
        certificate.certificateStatus());
    RagQueryResponse answer = unregistered ? unregisteredChatClient.query(query) : ragQueryClient.query(query);
    long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;

    warnIfUnsupportedResponseType(answer, sessionId);

    List<SourceResponse> sources = enrichSources(certificate.termsId(), answer);
    List<String> suggestedContacts = chatMessageMapper.toSuggestedContacts(answer);
    List<ClaimDocument> claimDocuments = chatMessageMapper.toClaimDocuments(answer);
    ChatMessage saved =
        chatMessageService.appendAssistantMessage(
            sessionId,
            answer.answer(),
            chatMessageMapper.toMetadataJson(
                sources, suggestedContacts, claimDocuments,
                answer.claimDocumentsNotice(), answer.claimDocumentsUrl(), latencyMs));

    return new ChatAnswerResponse(
        sessionId,
        saved.getId(),
        saved.getContent(),
        saved.getResponseType(),
        suggestedContacts,
        sources,
        claimDocuments,
        answer.claimDocumentsNotice(),
        answer.claimDocumentsUrl());
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
    if (termsId == null || answer.sources() == null || answer.sources().isEmpty()) {
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
