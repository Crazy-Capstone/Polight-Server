package polight.server.domain.analysis.dto;

import java.util.UUID;

/** Spring 서버가 AI 서버에 새 분석 작업을 전달할 때 사용하는 내부 계약. */
public record AiAnalysisRequest(UUID analysisResultId, String documentUrl) {}
