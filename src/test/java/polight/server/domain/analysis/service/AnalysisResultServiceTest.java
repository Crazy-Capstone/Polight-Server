package polight.server.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import polight.server.domain.insurance.service.PolicyDocumentService;

@ExtendWith(MockitoExtension.class)
class AnalysisResultServiceTest {

  private static final String OBJECT_KEY = "policy-documents/abc";

  @Mock private AnalysisResultRepository analysisResultRepository;
  @Mock private PolicyDocumentService policyDocumentService;
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
            analysisResultRepository, new AnalysisMapper(), policyDocumentService, eventPublisher);
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
    given(analysisResultRepository.findOneByDocumentId(documentId)).willReturn(Optional.empty());
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
    given(analysisResultRepository.findOneByDocumentId(documentId)).willReturn(Optional.of(failed));

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
    failed.completeWith("이전 요약", "{}", "text-embedding-3-small", 1536, 0.9f, true, LocalDateTime.now());
    failed.markFailed("두 번째 시도 실패", LocalDateTime.now());
    given(analysisResultRepository.findOneByDocumentId(documentId)).willReturn(Optional.of(failed));

    service.startAnalysis(userId, tripId, documentId);

    assertThat(failed.getSummary()).isNull();
    assertThat(failed.getRawResultJson()).isNull();
    assertThat(failed.getAccuracyScore()).isNull();
    assertThat(failed.isCoveragesComplete()).isFalse();
  }

  @Test
  void 진행_중인_분석은_그대로_돌려주고_이벤트를_발행하지_않는다() {
    given(analysisResultRepository.findOneByDocumentId(documentId))
        .willReturn(Optional.of(processingResult()));

    service.startAnalysis(userId, tripId, documentId);

    verify(eventPublisher, never()).publishEvent(any(AnalysisRequestedEvent.class));
    verify(analysisResultRepository, never()).save(any(AnalysisResult.class));
  }

  @Test
  void 완료된_분석은_다시_분석하지_않는다() {
    AnalysisResult completed = processingResult();
    completed.markCompleted(LocalDateTime.now());
    given(analysisResultRepository.findOneByDocumentId(documentId))
        .willReturn(Optional.of(completed));

    service.startAnalysis(userId, tripId, documentId);

    verify(eventPublisher, never()).publishEvent(any(AnalysisRequestedEvent.class));
    assertThat(completed.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
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
