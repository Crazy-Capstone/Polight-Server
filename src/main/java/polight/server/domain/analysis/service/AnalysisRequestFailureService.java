package polight.server.domain.analysis.service;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.analysis.repository.AnalysisResultRepository;

@Service
@RequiredArgsConstructor
public class AnalysisRequestFailureService {

  private final AnalysisResultRepository analysisResultRepository;

  /** 원 분석 트랜잭션은 이미 커밋됐으므로 독립 트랜잭션에서 전송 실패를 기록한다. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markFailed(UUID analysisResultId, String reason) {
    analysisResultRepository
        .findById(analysisResultId)
        .ifPresent(
            result -> {
              result.markFailed(reason, LocalDateTime.now());
              result.getDocument().markParseFailed();
            });
  }
}
