package polight.server.domain.analysis.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import polight.server.domain.analysis.entity.AnalysisStatus;

public record AnalysisResponse(
    UUID id,
    UUID documentId,
    AnalysisStatus status,
    String summary,
    String failureReason,
    LocalDateTime startedAt,
    LocalDateTime completedAt) {}
