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
   * <p>예외를 여기서 잡는 이유. Spring 은 반복 작업에 {@code TaskUtils.LOG_AND_SUPPRESS_ERROR_HANDLER}
   * 를 기본으로 붙이므로, 잡지 않아도 다음 주기는 계속 돈다. 스케줄이 멈출까 봐 잡는 것이 아니다. 무엇이 실패했는지 도메인 문맥이 담긴 메시지를 남기고, 한 주기의
   * 실패가 전체를 멈추지 않는다는 의도를 프레임워크 기본 동작에 맡기지 않고 코드에 드러내기 위한 것이다.
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
