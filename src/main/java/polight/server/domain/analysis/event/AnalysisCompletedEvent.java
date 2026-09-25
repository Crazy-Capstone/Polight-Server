package polight.server.domain.analysis.event;

import java.util.UUID;

/**
 * 분석 결과가 커밋된 뒤 약관을 연결하기 위한 이벤트.
 *
 * <p>담는 것은 id 하나뿐이다. 리스너가 자기 트랜잭션에서 다시 읽으므로 엔티티를 실어 보낼 이유가 없고, 실어 보내면 커밋 전에 만든 객체를 커밋 뒤에
 * 쓰게 되어 준영속 상태를 다루는 문제가 따라온다.
 */
public record AnalysisCompletedEvent(UUID analysisResultId) {}
