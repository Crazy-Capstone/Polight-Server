package polight.server.domain.analysis.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.analysis.dto.CoverageAnalysisResponse;
import polight.server.domain.analysis.dto.CoverageAnalysisResponse.CoverageItemResponse;
import polight.server.domain.analysis.dto.CoverageAnalysisResponse.DetailItemResponse;
import polight.server.domain.analysis.dto.CoverageAnalysisResponse.ExclusionResponse;
import polight.server.domain.analysis.dto.CoverageAnalysisResponse.RequiredDocumentResponse;
import polight.server.domain.analysis.dto.CoverageAnalysisResponse.SelectedConcernResponse;
import polight.server.domain.analysis.dto.CoverageAnalysisResponse.SubLimitResponse;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.analysis.entity.AnalysisStatus;
import polight.server.domain.analysis.entity.CoverageItem;
import polight.server.domain.analysis.repository.AnalysisResultRepository;
import polight.server.domain.analysis.repository.CoverageItemRepository;
import polight.server.domain.terms.entity.CoverageDetailItem;
import polight.server.domain.terms.entity.ExclusionCondition;
import polight.server.domain.terms.entity.PolicyTermsCoverage;
import polight.server.domain.terms.entity.RequiredDocument;
import polight.server.domain.terms.entity.SubCoverageLimit;
import polight.server.domain.terms.repository.CoverageDetailItemRepository;
import polight.server.domain.terms.repository.ExclusionConditionRepository;
import polight.server.domain.terms.repository.RequiredDocumentRepository;
import polight.server.domain.terms.repository.SubCoverageLimitRepository;
import polight.server.domain.concern.entity.Concern;
import polight.server.domain.concern.service.ConcernCoverageMatcher;
import polight.server.domain.insurance.service.PolicyDocumentService;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.trip.service.TripService;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

/**
 * 보장 확인 화면에 필요한 담보 목록을 조립한다.
 *
 * <p><b>응답은 두 영역을 합쳐 만든다.</b> 한쪽만으로는 화면이 서지 않는다.
 *
 * <ul>
 *   <li><b>가입 사실</b>({@code coverage_items}) — 무엇을 얼마에 가입했는지. 증권에서 나오고 그 사용자만의 것이다
 *   <li><b>약관 사실</b>({@code policy_terms_coverages}) — 면책·청구서류·세부한도. 약관에서 나오고 같은 상품 가입자 모두에게 같다
 * </ul>
 *
 * <p>증권에는 "해외여행중 상해의료비 3,000만원" 같은 표만 있어, 무엇이 면책이고 어떤 서류를 내야 하는지는 약관을 봐야 안다. 반대로 약관만으로는 그
 * 사람이 그 담보를 실제로 샀는지, 얼마에 샀는지 알 수 없다.
 *
 * <p>연결은 {@code CoverageItem.termsCoverage}가 쥐고 있고 없을 수 있다. 그때는 가입 정보만 내려간다 — 약관 쪽 필드는 빈 목록이다.
 *
 * <p>여기에 더해 걱정 매칭을 한다.
 *
 * <ul>
 *   <li>사용자가 고른 걱정에 해당하는 담보를 목록 위로 올린다
 *   <li>고른 걱정 중 어떤 것이 담보에서 확인되지 않았는지 알려준다
 * </ul>
 *
 * <p>두 번째가 이 화면의 핵심이다. 담보 목록만 보여주면 "내가 걱정하는 게 빠져 있다"를 사용자가 직접 찾아내야 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoverageAnalysisService {

  private final AnalysisResultRepository analysisResultRepository;
  private final CoverageItemRepository coverageItemRepository;
  private final CoverageDetailItemRepository coverageDetailItemRepository;
  private final SubCoverageLimitRepository subCoverageLimitRepository;
  private final RequiredDocumentRepository requiredDocumentRepository;
  private final ExclusionConditionRepository exclusionConditionRepository;
  private final PolicyDocumentService policyDocumentService;
  private final TripService tripService;
  private final ConcernCoverageMatcher matcher;

  public CoverageAnalysisResponse getCoverages(UUID userId, UUID tripId, UUID documentId) {
    policyDocumentService.getOwnedDocument(userId, tripId, documentId);

    AnalysisResult result =
        analysisResultRepository
            .findOneByDocumentId(documentId)
            .orElseThrow(() -> new BaseException(ErrorCode.ANALYSIS_RESULT_NOT_FOUND));

    // 분석이 끝나지 않았으면 판정 결과를 내려주지 않는다. 담보가 아직 0건이라
    // "고른 걱정이 전부 미보장"으로 보이는데, 그건 사실이 아니라 아직 모른다는 뜻이다.
    if (result.getStatus() != AnalysisStatus.COMPLETED) {
      return new CoverageAnalysisResponse(
          result.getId(), result.getStatus(), result.isCoveragesComplete(), List.of(), List.of());
    }

    Trip trip = tripService.getOwnedTrip(userId, tripId);
    List<Concern> selectedConcerns = trip.getConcerns();

    List<CoverageItem> items =
        coverageItemRepository.findByAnalysisResultIdOrderBySortOrderAsc(result.getId());

    Map<UUID, Set<Concern>> matchedByItem = matchAll(items, selectedConcerns);
    List<CoverageItemResponse> coverages = toResponses(items, matchedByItem);

    return new CoverageAnalysisResponse(
        result.getId(),
        result.getStatus(),
        result.isCoveragesComplete(),
        toSelectedConcerns(selectedConcerns, matchedByItem),
        coverages);
  }

  private Map<UUID, Set<Concern>> matchAll(
      List<CoverageItem> items, List<Concern> selectedConcerns) {
    return items.stream()
        .collect(
            Collectors.toMap(
                CoverageItem::getId,
                item -> matcher.match(item, selectedConcerns),
                (left, right) -> left,
                java.util.LinkedHashMap::new));
  }

  /** 고른 걱정 각각이 담보에서 확인됐는지. 응답 순서는 enum 선언 순서로 맞춘다. */
  private List<SelectedConcernResponse> toSelectedConcerns(
      List<Concern> selectedConcerns, Map<UUID, Set<Concern>> matchedByItem) {
    Set<Concern> coveredConcerns = new LinkedHashSet<>();
    matchedByItem.values().forEach(coveredConcerns::addAll);

    return selectedConcerns.stream()
        .sorted()
        .map(concern -> new SelectedConcernResponse(concern.name(), coveredConcerns.contains(concern)))
        .toList();
  }

  /**
   * 담보를 응답으로 바꾸면서 고른 걱정에 해당하는 것을 앞으로 보낸다.
   *
   * <p>약관 쪽 자식 4종은 <b>보장 규칙 단위</b>로 한 번에 가져와 묶은 뒤, 담보가 가리키는 규칙으로 되붙인다. 규칙별로 조회하면 규칙 수만큼 쿼리가
   * 나간다.
   *
   * <p>규칙 id로 모으는 것이라 <b>같은 규칙을 가리키는 담보 여럿이 같은 면책 목록을 공유</b>한다. 이것이 이 구조의 요점이다 — 사용자마다 복제하지
   * 않고 상품에 한 벌만 둔다.
   */
  private List<CoverageItemResponse> toResponses(
      List<CoverageItem> items, Map<UUID, Set<Concern>> matchedByItem) {
    if (items.isEmpty()) {
      return List.of();
    }

    // 약관 규칙이 연결되지 않은 담보가 섞여 있다. 그쪽은 조회 대상에서 빠지고 약관 필드가 빈 채로 나간다.
    List<UUID> termsCoverageIds =
        items.stream()
            .map(CoverageItem::getTermsCoverage)
            .filter(Objects::nonNull)
            .map(PolicyTermsCoverage::getId)
            .distinct()
            .toList();

    Map<UUID, List<CoverageDetailItem>> details = Map.of();
    Map<UUID, List<SubCoverageLimit>> subLimits = Map.of();
    Map<UUID, List<RequiredDocument>> documents = Map.of();
    Map<UUID, List<ExclusionCondition>> exclusions = Map.of();

    if (!termsCoverageIds.isEmpty()) {
      details =
          groupBy(
              coverageDetailItemRepository.findByTermsCoverageIdInOrderBySortOrderAsc(termsCoverageIds),
              child -> child.getTermsCoverage().getId());
      subLimits =
          groupBy(
              subCoverageLimitRepository.findByTermsCoverageIdInOrderBySortOrderAsc(termsCoverageIds),
              child -> child.getTermsCoverage().getId());
      documents =
          groupBy(
              requiredDocumentRepository.findByTermsCoverageIdInOrderBySortOrderAsc(termsCoverageIds),
              child -> child.getTermsCoverage().getId());
      exclusions =
          groupBy(
              exclusionConditionRepository.findByTermsCoverageIdInOrderBySortOrderAsc(termsCoverageIds),
              child -> child.getTermsCoverage().getId());
    }

    List<CoverageItemResponse> responses = new ArrayList<>();
    for (CoverageItem item : items) {
      Set<Concern> matched = matchedByItem.getOrDefault(item.getId(), Set.of());
      // 약관 규칙이 없으면 null 이고, 아래 getOrDefault 가 전부 빈 목록을 돌려준다.
      UUID rulesKey = item.getTermsCoverage() == null ? null : item.getTermsCoverage().getId();
      responses.add(
          new CoverageItemResponse(
              item.getId(),
              item.getTitle(),
              item.getSubtitle(),
              item.getCategory(),
              item.getCoverageStatus(),
              item.isCovered(),
              item.getLimitLabel(),
              item.getLimitAmount(),
              item.getLimitCurrency(),
              item.getConditions(),
              matched.stream().sorted().map(Concern::name).toList(),
              rulesOf(details, rulesKey).stream()
                  .map(
                      child ->
                          new DetailItemResponse(
                              child.getTitle(), child.getSubtitle(), child.isCovered()))
                  .toList(),
              rulesOf(subLimits, rulesKey).stream()
                  .map(
                      child ->
                          new SubLimitResponse(
                              child.getLabel(),
                              child.getValue(),
                              child.getDescription(),
                              child.getLimitAmount(),
                              child.getLimitCurrency()))
                  .toList(),
              rulesOf(documents, rulesKey).stream()
                  .map(
                      child ->
                          new RequiredDocumentResponse(child.getDocumentName(), child.isMandatory()))
                  .toList(),
              rulesOf(exclusions, rulesKey).stream()
                  .map(
                      child ->
                          new ExclusionResponse(
                              child.getTitle(),
                              child.getDescription(),
                              child.getSourceText(),
                              child.getSeverity().name()))
                  .toList()));
    }

    // 고른 걱정에 해당하는 담보를 위로. 그 안에서는 원래 순서(중요도 순)를 유지하므로 안정 정렬이어야 한다.
    responses.sort(Comparator.comparing(response -> response.matchedConcerns().isEmpty()));
    return responses;
  }

  /**
   * 규칙에 딸린 자식을 꺼낸다. 규칙이 연결되지 않은 담보면 빈 목록이다.
   *
   * <p>{@code getOrDefault}를 직접 부르지 않는 이유: 연결된 규칙이 하나도 없으면 위에서 맵이 {@link Map#of()}로 남는데,
   * 불변 맵은 null 키 조회에 {@code NullPointerException}을 던진다. 규칙이 없는 담보의 키가 바로 null이라, 약관을 아직 아무것도
   * 연결하지 않은 지금 상태에서 조회가 통째로 터진다.
   */
  private static <T> List<T> rulesOf(Map<UUID, List<T>> byRule, UUID rulesKey) {
    if (rulesKey == null) {
      return List.of();
    }
    return byRule.getOrDefault(rulesKey, List.of());
  }

  private <T> Map<UUID, List<T>> groupBy(
      List<T> children, java.util.function.Function<T, UUID> keyMapper) {
    return children.stream().collect(Collectors.groupingBy(keyMapper));
  }
}
