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
 * <p>{@code insurerName}/{@code productName}은 증권 분석일 때만 채워진다. 이 두 값이 <b>어느 약관인가</b>를 찾는
 * 열쇠다 — {@code analysis_results}에 저장한 뒤 {@code PolicyTermsMatchingService}가 {@code policy_terms}를
 * 찾아 연결한다. 약관 식별은 AI가 아니라 백엔드 몫이다. AI 레포에도 이름이 비슷한 코드가 있지만({@code terms_matcher},
 * {@code clause_matcher}) 로컬 데모·평가용 스크립트라 실제 서비스 경로에서 호출되지 않는다.
 *
 * <p>이 값으로 {@code policies} 행을 만들지는 못한다 — {@code start_date}/{@code end_date}가 NOT NULL인데 콜백에
 * 보험기간이 없다. 보험기간이 들어오면 {@code raw_result_json}에 통째로 남겨 둔 본문에서 꺼내 쓴다.
 *
 * <p><b>근거 조항({@code sources[]})은 오지 않는다.</b> 약관 특약명, 면책 조항, 청구 서류도 마찬가지다. AI는 증권에서 읽은 것만
 * 보내고, 증권에는 그런 내용이 없다(약관에 있다). 담보를 약관 조항에 잇는 일은 백엔드가 약관을 식별한 뒤에 한다. 그래서 {@code
 * coverage_item_sources}를 채우는 코드가 아직 없고, 그 테이블은 비어 있다.
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
