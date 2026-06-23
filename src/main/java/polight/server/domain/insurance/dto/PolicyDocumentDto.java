package polight.server.domain.insurance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;
import polight.server.domain.insurance.entity.DocumentParseStatus;

public final class PolicyDocumentDto {
  private PolicyDocumentDto() {}

  @Schema(name = "PolicyDocumentResponse")
  public record Response(UUID documentId, String originalFilename, String contentType, Long fileSize,
                         DocumentParseStatus parseStatus, UUID tripId, UUID policyId,
                         LocalDateTime uploadedAt) {}

  @Schema(name = "AnalysisRequestResponse")
  public record AnalysisRequest(UUID documentId, UUID analysisId,
                                DocumentParseStatus parseStatus) {}

  @Schema(name = "PolicyDocumentAnalysisResponse")
  public record AnalysisResponse(UUID documentId, UUID analysisId, DocumentParseStatus parseStatus,
                                 UUID policyId, Long coverageCount, Integer coverageScore,
                                 String errorMessage) {}
}
