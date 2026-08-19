package polight.server.domain.analysis.dto;

import java.util.List;
import java.util.UUID;

/**
 * AI 서버가 분석 완료 시 보내는 콜백 본문.
 *
 * <p>{@code isCovered}와 {@code sortOrder}는 받지 않는다. 전자는 {@code coverageStatus}에서 파생하고 후자는 배열 순서로
 * 부여한다. AI가 보낸 값과 파생/부여한 값이 어긋날 가능성을 없애기 위해 규칙을 백엔드 한 곳에만 둔다.
 *
 * <p>{@code coveragesComplete}는 담보 목록이 증권의 보장내용 표 전체인지를 뜻한다. 판단할 수 있는 것은 추출한 쪽뿐이라 AI가 보낸다.
 * 값이 없으면 {@code false}로 본다 -- 단정하지 않는 쪽이 안전하다.
 *
 * <p>{@code insurerName}/{@code productName}은 증권 분석일 때만 채워진다. 지금은 이 값으로 {@code policies} 행을 만들 수
 * 없다 — {@code start_date}/{@code end_date}가 NOT NULL인데 콜백에 보험기간이 없다. 값이 유실되지 않도록 콜백 본문 전체를
 * {@code raw_result_json}에 저장해 두고, 보험기간이 들어오면 그때 꺼내 쓴다.
 */
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
      List<ExclusionPayload> exclusions) {}

  public record DetailItemPayload(String title, String subtitle, Boolean isCovered) {}

  public record SubLimitPayload(
      String label, String value, String description, Long limitAmount, String limitCurrency) {}

  public record RequiredDocumentPayload(String documentName, Boolean isMandatory) {}

  public record ExclusionPayload(
      String title, String description, String sourceText, String severity) {}
}
