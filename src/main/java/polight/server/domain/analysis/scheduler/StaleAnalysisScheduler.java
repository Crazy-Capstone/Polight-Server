package polight.server.domain.analysis.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import polight.server.domain.analysis.service.StaleAnalysisTimeoutService;

/**
 * 응답 시간을 초과한 분석을 주기적으로 실패 처리한다.
 *
 * <p>스케줄 실행과 실제 판정을 나눈 이유: 판정 로직을 트랜잭션 경계가 있는 서비스에 두면 테스트에서 스케줄러 없이 직접 호출할 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "analysis.timeout.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class StaleAnalysisScheduler {

  private final StaleAnalysisTimeoutService staleAnalysisTimeoutService;

  /**
   * {@code fixedDelay}를 쓴다. 한 번의 실행이 길어져도 다음 실행이 겹치지 않는다.
   *
   * <p>예외를 삼키는 이유: {@code @Scheduled} 메서드에서 예외가 올라가면 해당 작업이 더 이상 실행되지 않는다. DB가 일시적으로 끊긴 것 때문에
   * 타임아웃 처리가 영구히 멈추면 안 된다.
   */
  @Scheduled(
      fixedDelayString = "${analysis.timeout.check-interval}",
      initialDelayString = "${analysis.timeout.check-interval}")
  public void failTimedOutAnalyses() {
    try {
      int count = staleAnalysisTimeoutService.failTimedOutAnalyses();
      if (count > 0) {
        log.info("응답 시간을 초과한 분석 {}건을 실패로 내렸습니다.", count);
      }
    } catch (RuntimeException exception) {
      log.error("분석 타임아웃 처리에 실패했습니다. 다음 주기에 다시 시도합니다.", exception);
    }
  }
}
