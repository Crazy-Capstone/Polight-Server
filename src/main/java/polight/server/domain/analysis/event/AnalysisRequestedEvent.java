package polight.server.domain.analysis.event;

import java.util.UUID;

/** DB 커밋 후 AI 분석 요청을 시작하기 위한 이벤트. Presigned URL 대신 영구 object key만 담는다. */
public record AnalysisRequestedEvent(UUID analysisResultId, String objectKey) {}
