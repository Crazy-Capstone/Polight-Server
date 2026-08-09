package polight.server.domain.insurance.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import polight.server.domain.insurance.entity.DocumentParseStatus;

public record PolicyDocumentResponse(
    UUID id,
    UUID tripId,
    String originalFilename,
    String contentType,
    Long fileSize,
    DocumentParseStatus parseStatus,
    LocalDateTime uploadedAt) {}
