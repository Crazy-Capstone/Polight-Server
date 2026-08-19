package polight.server.domain.analysis.event;

import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import polight.server.domain.analysis.client.AiAnalysisClient;
import polight.server.domain.analysis.dto.AiAnalysisRequest;
import polight.server.domain.analysis.service.AnalysisRequestFailureService;
import polight.server.domain.insurance.storage.PolicyDocumentUrlProvider;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class AnalysisRequestEventListener {

  private final PolicyDocumentUrlProvider urlProvider;
  private final AiAnalysisClient aiAnalysisClient;
  private final AnalysisRequestFailureService failureService;

  /** 분석 레코드가 DB에 보인 뒤 같은 요청 스레드에서 FastAPI를 동기 호출한다. */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void requestAnalysis(AnalysisRequestedEvent event) {
    try {
      URI documentUrl = urlProvider.createDownloadUrl(event.objectKey());
      aiAnalysisClient.requestAnalysis(
          new AiAnalysisRequest(
              event.analysisResultId(),
              event.userId(),
              event.tripId(),
              event.documentId(),
              documentUrl.toString(),
              event.documentKind().name()));
    } catch (RuntimeException exception) {
      log.error("AI 분석 요청 최종 실패: analysisResultId={}", event.analysisResultId(), exception);
      failureService.markFailed(event.analysisResultId(), "AI 서버 분석 요청 전송 실패");
    }
  }
}
