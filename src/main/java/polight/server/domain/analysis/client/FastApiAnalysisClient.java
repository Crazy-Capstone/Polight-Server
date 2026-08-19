package polight.server.domain.analysis.client;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import polight.server.domain.analysis.dto.AiAnalysisRequest;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;
import polight.server.global.security.InternalApiKeyFilter;

@Slf4j
@Component
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class FastApiAnalysisClient implements AiAnalysisClient {

  private final RestClient restClient;
  private final String analysisPath;
  private final String internalApiKey;
  private final int maxAttempts;
  private final Duration retryDelay;

  public FastApiAnalysisClient(
      @Qualifier("aiRestClient") RestClient restClient,
      @Value("${ai.server.analysis-path}") String analysisPath,
      @Value("${internal.api-key:}") String internalApiKey,
      @Value("${ai.server.max-attempts:3}") int maxAttempts,
      @Value("${ai.server.retry-delay:500ms}") Duration retryDelay) {
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("ai.server.max-attempts는 1 이상이어야 합니다.");
    }
    this.restClient = restClient;
    this.analysisPath = analysisPath;
    this.internalApiKey = internalApiKey;
    this.maxAttempts = maxAttempts;
    this.retryDelay = retryDelay;
  }

  @Override
  public void requestAnalysis(AiAnalysisRequest request) {
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        restClient
            .post()
            .uri(analysisPath)
            .header(InternalApiKeyFilter.HEADER_NAME, internalApiKey)
            .body(request)
            .retrieve()
            .toBodilessEntity();
        log.info("AI 분석 요청 완료: analysisResultId={}", request.analysisResultId());
        return;
      } catch (RuntimeException exception) {
        boolean retryable = isRetryable(exception);
        if (!retryable || attempt == maxAttempts) {
          throw new BaseException(ErrorCode.AI_ANALYSIS_REQUEST_FAILED, exception);
        }
        log.warn(
            "AI 분석 요청 재시도: analysisResultId={}, attempt={}/{}, cause={}",
            request.analysisResultId(),
            attempt,
            maxAttempts,
            exception.getClass().getSimpleName());
        waitBeforeRetry();
      }
    }
  }

  private boolean isRetryable(RuntimeException exception) {
    if (exception instanceof ResourceAccessException) {
      return true;
    }
    if (exception instanceof HttpStatusCodeException statusException) {
      HttpStatusCode status = statusException.getStatusCode();
      return status.is5xxServerError() || status.value() == 429;
    }
    return false;
  }

  private void waitBeforeRetry() {
    try {
      Thread.sleep(retryDelay.toMillis());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new BaseException(ErrorCode.AI_ANALYSIS_REQUEST_FAILED, exception);
    }
  }
}
