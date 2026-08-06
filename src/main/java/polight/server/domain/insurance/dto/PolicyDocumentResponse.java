package polight.server.domain.insurance.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import polight.server.domain.insurance.entity.DocumentParseStatus;
import polight.server.domain.insurance.entity.PolicyDocument;

public record PolicyDocumentResponse(
    UUID id,
    UUID tripId,
    String originalFilename,
    String contentType,
    Long fileSize,
    DocumentParseStatus parseStatus,
    LocalDateTime uploadedAt) {

  public static PolicyDocumentResponse from(PolicyDocument document) {
    return new PolicyDocumentResponse(
        document.getId(),
        document.getTrip().getId(),
        document.getOriginalFilename(),
        document.getContentType(),
        document.getFileSize(),
        document.getParseStatus(),
        document.getUploadedAt());
  }
}
