package polight.server.domain.analysis.dto;

import java.util.List;
import java.util.UUID;
import polight.server.domain.analysis.entity.AnalysisStatus;
import polight.server.domain.analysis.entity.CoverageStatus;

/**
 * 보장 확인 화면에 내려주는 분석 결과.
 *
 * <p>사용자가 고른 걱정에 해당하는 담보를 목록 위로 올려 보낸다. 정렬을 서버가 하는 이유는 매칭 규칙(키워드)이 서버에만 있기 때문이다.
 *
 * <p>사용자에게 보이는 문구는 담지 않는다. 걱정 라벨은 프론트가 코드로 찾아 쓴다. 문구를 양쪽에 두면 서로 어긋난다.
 *
 * @param status {@code COMPLETED}가 아니면 {@code coverages}와 {@code selectedConcerns}는 빈 목록이다. 분석 중에
 *     "미보장"으로 보이는 것을 막기 위해 판정 결과를 아예 내려주지 않는다
 * @param coveragesComplete 담보 목록이 증권 보장내용 표 전체인지. {@code false}면 목록에 없는 담보를 "미가입"으로 단정할 수 없다.
 *     현재는 항상 {@code false}다
 */
public record CoverageAnalysisResponse(
    UUID analysisResultId,
    AnalysisStatus status,
    boolean coveragesComplete,
    List<SelectedConcernResponse> selectedConcerns,
    List<CoverageItemResponse> coverages) {

  /**
   * 사용자가 고른 걱정과 그 걱정이 담보에서 확인됐는지.
   *
   * @param covered 이 걱정에 해당하는 담보가 하나라도 있으면 true. <b>false를 "가입하지 않았다"로 단정하면 안 된다</b> — 키워드 매칭이
   *     못 잡았거나 증권 추출이 빠뜨렸을 수 있다({@code coveragesComplete} 참고)
   */
  public record SelectedConcernResponse(String code, boolean covered) {}

  /** @param matchedConcerns 이 담보가 어떤 걱정에 해당하는지. 화면에서 강조 표시에 쓸 수 있다 */
  public record CoverageItemResponse(
      UUID id,
      String title,
      String subtitle,
      String category,
      CoverageStatus coverageStatus,
      boolean isCovered,
      String limitLabel,
      Long limitAmount,
      String limitCurrency,
      String conditions,
      List<String> matchedConcerns,
      List<DetailItemResponse> detailItems,
      List<SubLimitResponse> subLimits,
      List<RequiredDocumentResponse> requiredDocuments,
      List<ExclusionResponse> exclusions) {}

  public record DetailItemResponse(String title, String subtitle, boolean isCovered) {}

  public record SubLimitResponse(
      String label, String value, String description, Long limitAmount, String limitCurrency) {}

  public record RequiredDocumentResponse(String documentName, boolean isMandatory) {}

  public record ExclusionResponse(
      String title, String description, String sourceText, String severity) {}
}
