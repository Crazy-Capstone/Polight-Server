package polight.server.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @Scheduled} 활성화. 현재 사용처는 분석 타임아웃 처리 하나뿐이다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
