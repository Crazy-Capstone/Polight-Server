package polight.server.domain.analysis.event;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import polight.server.domain.analysis.client.AiAnalysisClient;
import polight.server.domain.analysis.dto.AiAnalysisRequest;
import polight.server.domain.analysis.service.AnalysisRequestFailureService;
import polight.server.domain.insurance.storage.PolicyDocumentUrlProvider;

@ExtendWith(MockitoExtension.class)
class AnalysisRequestEventListenerTest {

  @Mock private PolicyDocumentUrlProvider urlProvider;
  @Mock private AiAnalysisClient aiAnalysisClient;
  @Mock private AnalysisRequestFailureService failureService;

  @Test
  void createsUrlAtHandlingTimeAndPassesItWithAnalysisId() {
    UUID analysisId = UUID.randomUUID();
    given(urlProvider.createDownloadUrl("policy-documents/key"))
        .willReturn(URI.create("https://example.com/presigned"));
    AnalysisRequestEventListener listener =
        new AnalysisRequestEventListener(urlProvider, aiAnalysisClient, failureService);

    listener.requestAnalysis(new AnalysisRequestedEvent(analysisId, "policy-documents/key"));

    ArgumentCaptor<AiAnalysisRequest> request = ArgumentCaptor.forClass(AiAnalysisRequest.class);
    verify(aiAnalysisClient).requestAnalysis(request.capture());
    org.assertj.core.api.Assertions.assertThat(request.getValue().analysisResultId())
        .isEqualTo(analysisId);
    org.assertj.core.api.Assertions.assertThat(request.getValue().documentUrl())
        .isEqualTo("https://example.com/presigned");
  }

  @Test
  void marksAnalysisFailedWhenDispatchUltimatelyFails() {
    UUID analysisId = UUID.randomUUID();
    given(urlProvider.createDownloadUrl("key")).willThrow(new IllegalStateException("S3 error"));
    AnalysisRequestEventListener listener =
        new AnalysisRequestEventListener(urlProvider, aiAnalysisClient, failureService);

    listener.requestAnalysis(new AnalysisRequestedEvent(analysisId, "key"));

    verify(failureService).markFailed(analysisId, "AI 서버 분석 요청 전송 실패");
  }
}
