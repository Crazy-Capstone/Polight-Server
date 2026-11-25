package polight.server.domain.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;


public record AnalysisCallbackRequest(
    UUID analysisResultId,
    String status,
    String summary,
    String rawResultJson,
    String embeddingModel,
    Integer embeddingDimension,
    Float accuracyScore,
    Boolean coveragesComplete,
    String insurerName,
    String productName,
    LocalDate startDate,
    LocalDate endDate,
    List<CoverageItemPayload> coverageItems) {

  public record CoverageItemPayload(
      String title,
      String subtitle,
      String category,
      String coverageStatus,
      String limitLabel,
      Long limitAmount,
      String limitCurrency,
      String conditions,
      List<DetailItemPayload> detailItems,
      List<SubLimitPayload> subLimits,
      List<RequiredDocumentPayload> requiredDocuments,
      List<ExclusionPayload> exclusions,
      @Schema(description = "FIXED, PROPORTIONAL, INDEMNITY, DAILY. 누락 또는 분류 불가 값은 null로 저장",
          example = "INDEMNITY", nullable = true)
          String paymentType) {}

  public record DetailItemPayload(String title, String subtitle, Boolean isCovered) {}

  public record SubLimitPayload(
      String label, String value, String description, Long limitAmount, String limitCurrency) {}

  public record RequiredDocumentPayload(String documentName, Boolean isMandatory) {}

  public record ExclusionPayload(
      String title, String description, String sourceText, String severity) {}
}
