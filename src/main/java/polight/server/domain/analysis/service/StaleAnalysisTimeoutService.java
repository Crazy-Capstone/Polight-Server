package polight.server.domain.analysis.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.analysis.entity.AnalysisStatus;
import polight.server.domain.analysis.repository.AnalysisResultRepository;

/**
 * 콜백이 오지 않아 {@code PROCESSING}에 멈춘 분석을 실패로 확정한다.
 *
 * <p>AI 서버가 분석 요청을 정상으로 받은 뒤 콜백을 보내지 않으면(프로세스 종료, 콜백 3회 재시도 소진 등) 분석은 영구히 {@code PROCESSING}에
 * 머문다. 프론트엔드는 상태가 바뀔 때까지 폴링하도록 안내돼 있어 그 화면은 끝나지 않는다. 사용자에게는 실패보다 이쪽이 더 나쁘다 — 실패했다는 사실조차 알 수
 * 없고, 재시도할 수도 없기 때문이다.
 *
 * <p>여기서 {@code FAILED}로 내리면 사용자는 상황을 알게 되고 {@code POST .../analysis}로 재시도할 수 있다.
 */
@Slf4j
@Service
public class StaleAnalysisTimeoutService {

  private final AnalysisResultRepository analysisResultRepository;
  private final Duration timeoutAfter;

  public StaleAnalysisTimeoutService(
      AnalysisResultRepository analysisResultRepository,
      @Value("${analysis.timeout.after}") Duration timeoutAfter) {
    if (timeoutAfter.isZero() || timeoutAfter.isNegative()) {
      throw new IllegalStateException("analysis.timeout.after 는 0보다 커야 합니다.");
    }
    this.analysisResultRepository = analysisResultRepository;
    this.timeoutAfter = timeoutAfter;
  }

  /**
   * 제한 시간을 넘긴 진행 중 분석을 모두 실패로 표시한다.
   *
   * <p>여러 인스턴스가 동시에 실행해도 안전하다. 조회 조건이 {@code PROCESSING}이므로 한쪽이 먼저 내린 건은 다른 쪽 결과에 들어오지 않고, 겹쳐
   * 들어와도 같은 값을 쓴다.
   *
   * @return 실패로 내린 건수
   */
  @Transactional
  public int failTimedOutAnalyses() {
    LocalDateTime threshold = LocalDateTime.now().minus(timeoutAfter);
    List<AnalysisResult> staleResults =
        analysisResultRepository.findByStatusAndStartedAtBefore(
            AnalysisStatus.PROCESSING, threshold);
    if (staleResults.isEmpty()) {
      return 0;
    }

    String reason = "AI 서버 응답 시간 초과 (" + timeoutAfter.toMinutes() + "분)";
    for (AnalysisResult result : staleResults) {
      result.markFailed(reason, LocalDateTime.now());
      result.getDocument().markParseFailed();
      log.warn(
          "응답 시간을 초과한 분석을 실패로 내립니다: analysisResultId={}, startedAt={}",
          result.getId(),
          result.getStartedAt());
    }

    return staleResults.size();
  }
}
