package polight.server.domain.analysis.dto;

import java.util.UUID;

/** AI 서버가 분석 실패 시 보내는 콜백 본문. */
public record AnalysisFailureCallbackRequest(
    UUID analysisResultId, String status, String errorMessage) {}
