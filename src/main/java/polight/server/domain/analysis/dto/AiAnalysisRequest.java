package polight.server.domain.analysis.dto;

import java.util.UUID;


public record AiAnalysisRequest(
    UUID analysisResultId,
    UUID userId,
    UUID tripId,
    UUID documentId,
    String downloadUrl,
    String documentType) {}
