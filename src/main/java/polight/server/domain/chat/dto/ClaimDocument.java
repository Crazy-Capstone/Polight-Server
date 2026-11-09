package polight.server.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaimDocument(
    String documentName,
    @Schema(description = "REQUIRED, CONDITIONAL, UNKNOWN 등의 서류 필요 여부", example = "REQUIRED")
        String requirementType,
    String conditionText,
    String coverageTitle,
    UUID sourceChunkId,
    String sourceQuote,
    String sectionTitle,
    Integer pageStart,
    Integer pageEnd) {}
