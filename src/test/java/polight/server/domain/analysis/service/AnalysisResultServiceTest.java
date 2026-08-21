package polight.server.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.analysis.entity.AnalysisStatus;
import polight.server.domain.analysis.event.AnalysisRequestedEvent;
import polight.server.domain.analysis.mapper.AnalysisMapper;
import polight.server.domain.analysis.repository.AnalysisResultRepository;
import polight.server.domain.insurance.entity.DocumentKind;
import polight.server.domain.insurance.entity.DocumentParseStatus;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;
import polight.server.domain.insurance.service.PolicyDocumentService;
import polight.server.domain.rag.service.PolicyChunkQueryService;
import polight.server.domain.terms.entity.PolicyTerms;

@ExtendWith(MockitoExtension.class)
class AnalysisResultServiceTest {

  private static final String OBJECT_KEY = "policy-documents/abc";

  @Mock private AnalysisResultRepository analysisResultRepository;
  @Mock private PolicyDocumentService policyDocumentService;
  @Mock private PolicyChunkQueryService policyChunkQueryService;
  @Mock private ApplicationEventPublisher eventPublisher;

  private AnalysisResultService service;
  private UUID userId;
  private UUID tripId;
  private UUID documentId;
  private PolicyDocument document;

  @BeforeEach
  void setUp() {
    // 매퍼는 순수 변환이라 목이 아니라 실제 구현을 쓴다.
    service =
        new AnalysisResultService(
            analysisResultRepository,
            new AnalysisMapper(),
            policyDocumentService,
            policyChunkQueryService,
            eventPublisher);
    userId = UUID.randomUUID();
    tripId = UUID.randomUUID();
    documentId = UUID.randomUUID();
    document =
        PolicyDocument.builder()
            .originalFilename("증권.pdf")
            .storedFilePath(OBJECT_KEY)
            .documentKind(DocumentKind.CERTIFICATE)
            .build();
    given(policyDocumentService.getOwnedDocument(userId, tripId, documentId)).willReturn(document);
  }

  @Test
  void 분석이_없으면_새로_만들고_요청_이벤트를_발행한다() {
    given(analysisResultRepository.findOneByDocumentIdForUpdate(documentId)).willReturn(Optional.empty());
    given(analysisResultRepository.save(any(AnalysisResult.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    service.startAnalysis(userId, tripId, documentId);

    AnalysisRequestedEvent event = publishedEvent();
    assertThat(event.objectKey()).isEqualTo(OBJECT_KEY);
    assertThat(event.documentKind()).isEqualTo(DocumentKind.CERTIFICATE);
  }

  @Test
  void 실패한_분석은_다시_시작하고_요청_이벤트를_다시_발행한다() {
    AnalysisResult failed = processingResult();
    failed.markFailed("AI 서버 분석 요청 전송 실패", LocalDateTime.now());
    document.markParseFailed();
    given(analysisResultRepository.findOneByDocumentIdForUpdate(documentId)).willReturn(Optional.of(failed));

    service.startAnalysis(userId, tripId, documentId);

    // 재시도의 핵심: 새 행을 만들지 않고 기존 행을 되돌린다.
    verify(analysisResultRepository, never()).save(any(AnalysisResult.class));
    assertThat(failed.getStatus()).isEqualTo(AnalysisStatus.PROCESSING);
    assertThat(failed.getFailureReason()).isNull();
    assertThat(failed.getCompletedAt()).isNull();
    assertThat(document.getParseStatus()).isEqualTo(DocumentParseStatus.UPLOADED);
    assertThat(publishedEvent().objectKey()).isEqualTo(OBJECT_KEY);
  }

  @Test
  void 실패한_분석을_되돌릴_때_이전_시도의_산출물을_비운다() {
    AnalysisResult failed = processingResult();
    failed.completeWith(
        "이전 요약",
        "{}",
        "text-embedding-3-small",
        1536,
        0.9f,
        true,
        "삼성화재",
        "해외여행보험",
        LocalDate.of(2026, 3, 1),
        LocalDate.of(2026, 3, 6),
        LocalDateTime.now());
    failed.linkTerms(PolicyTerms.official("삼성화재", "해외여행보험", null, LocalDate.of(2026, 1, 1)));
    failed.markFailed("두 번째 시도 실패", LocalDateTime.now());
    given(analysisResultRepository.findOneByDocumentIdForUpdate(documentId)).willReturn(Optional.of(failed));

    service.startAnalysis(userId, tripId, documentId);

    assertThat(failed.getSummary()).isNull();
    assertThat(failed.getRawResultJson()).isNull();
    assertThat(failed.getAccuracyScore()).isNull();
    assertThat(failed.isCoveragesComplete()).isFalse();
    // 보험사/상품명과 약관 연결도 함께 비워야 한다. 재분석이 다른 이름을 읽으면 남아 있는 연결은
    // 다른 상품의 약관을 가리키게 된다.
    assertThat(failed.getInsurerName()).isNull();
    assertThat(failed.getProductName()).isNull();
    assertThat(failed.getMatchedTerms()).isNull();
    assertThat(failed.getInsuranceStartDate()).isNull();
    assertThat(failed.getInsuranceEndDate()).isNull();
  }

  @Test
  void 진행_중인_분석은_그대로_돌려주고_이벤트를_발행하지_않는다() {
    given(analysisResultRepository.findOneByDocumentIdForUpdate(documentId))
        .willReturn(Optional.of(processingResult()));

    service.startAnalysis(userId, tripId, documentId);

    verify(eventPublisher, never()).publishEvent(any(AnalysisRequestedEvent.class));
    verify(analysisResultRepository, never()).save(any(AnalysisResult.class));
  }

  @Test
  void 완료된_분석은_다시_분석하지_않는다() {
    AnalysisResult completed = processingResult();
    completed.markCompleted(LocalDateTime.now());
    given(analysisResultRepository.findOneByDocumentIdForUpdate(documentId))
        .willReturn(Optional.of(completed));

    service.startAnalysis(userId, tripId, documentId);

    verify(eventPublisher, never()).publishEvent(any(AnalysisRequestedEvent.class));
    assertThat(completed.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
  }

  @Test
  void 색인된_조각이_남은_분석은_재시도를_거절한다() {
    // AI 서버가 같은 analysis_result_id 로 chunk_index 0 부터 다시 넣으면
    // uk_policy_chunks_analysis_chunk_index 위반으로 매번 실패한다. 조용히 재시도시키는 대신
    // 재업로드가 필요하다고 알려준다.
    AnalysisResult failed = processingResult();
    failed.markFailed("약관 파싱 실패", LocalDateTime.now());
    given(analysisResultRepository.findOneByDocumentIdForUpdate(documentId))
        .willReturn(Optional.of(failed));
    given(policyChunkQueryService.hasChunksFor(failed.getId())).willReturn(true);

    assertThatThrownBy(() -> service.startAnalysis(userId, tripId, documentId))
        .isInstanceOf(BaseException.class)
        .extracting(exception -> ((BaseException) exception).getErrorCode())
        .isEqualTo(ErrorCode.ANALYSIS_RETRY_NOT_SUPPORTED);

    // 거절했으므로 상태를 되돌리지도, AI 요청을 보내지도 않는다.
    assertThat(failed.getStatus()).isEqualTo(AnalysisStatus.FAILED);
    verify(eventPublisher, never()).publishEvent(any(AnalysisRequestedEvent.class));
  }

  @Test
  void 조각이_없으면_재시도를_허용한다() {
    // 증권은 청킹을 하지 않으므로 항상 이쪽이다.
    AnalysisResult failed = processingResult();
    failed.markFailed("AI 서버 분석 요청 전송 실패", LocalDateTime.now());
    given(analysisResultRepository.findOneByDocumentIdForUpdate(documentId))
        .willReturn(Optional.of(failed));
    given(policyChunkQueryService.hasChunksFor(failed.getId())).willReturn(false);

    service.startAnalysis(userId, tripId, documentId);

    assertThat(failed.getStatus()).isEqualTo(AnalysisStatus.PROCESSING);
    assertThat(publishedEvent().objectKey()).isEqualTo(OBJECT_KEY);
  }

  private AnalysisResult processingResult() {
    return AnalysisResult.builder().document(document).startedAt(LocalDateTime.now()).build();
  }

  private AnalysisRequestedEvent publishedEvent() {
    ArgumentCaptor<AnalysisRequestedEvent> captor =
        ArgumentCaptor.forClass(AnalysisRequestedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    return captor.getValue();
  }
}
