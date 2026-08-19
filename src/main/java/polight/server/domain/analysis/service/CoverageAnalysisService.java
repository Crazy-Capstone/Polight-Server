package polight.server.domain.analysis.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import polight.server.domain.analysis.entity.CoverageDetailItem;
import polight.server.domain.analysis.entity.CoverageItem;
import polight.server.domain.analysis.entity.ExclusionCondition;
import polight.server.domain.analysis.entity.RequiredDocument;
import polight.server.domain.analysis.entity.SubCoverageLimit;
import polight.server.domain.analysis.repository.AnalysisResultRepository;
import polight.server.domain.analysis.repository.CoverageDetailItemRepository;
import polight.server.domain.analysis.repository.CoverageItemRepository;
import polight.server.domain.analysis.repository.ExclusionConditionRepository;
import polight.server.domain.analysis.repository.RequiredDocumentRepository;
import polight.server.domain.analysis.repository.SubCoverageLimitRepository;
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
 * <p>담보 자체는 AI가 증권에서 뽑아 콜백으로 저장한 것을 그대로 쓰고, 이 서비스가 더하는 것은 두 가지다.
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
   * <p>자식 4종은 담보별로 조회하지 않고 한 번에 가져와 묶는다. 담보가 20건이면 담보별 조회는 쿼리 80개가 된다.
   */
  private List<CoverageItemResponse> toResponses(
      List<CoverageItem> items, Map<UUID, Set<Concern>> matchedByItem) {
    if (items.isEmpty()) {
      return List.of();
    }

    List<UUID> itemIds = items.stream().map(CoverageItem::getId).toList();
    Map<UUID, List<CoverageDetailItem>> details =
        groupBy(
            coverageDetailItemRepository.findByCoverageItemIdInOrderBySortOrderAsc(itemIds),
            child -> child.getCoverageItem().getId());
    Map<UUID, List<SubCoverageLimit>> subLimits =
        groupBy(
            subCoverageLimitRepository.findByCoverageItemIdInOrderBySortOrderAsc(itemIds),
            child -> child.getCoverageItem().getId());
    Map<UUID, List<RequiredDocument>> documents =
        groupBy(
            requiredDocumentRepository.findByCoverageItemIdInOrderBySortOrderAsc(itemIds),
            child -> child.getCoverageItem().getId());
    Map<UUID, List<ExclusionCondition>> exclusions =
        groupBy(
            exclusionConditionRepository.findByCoverageItemIdInOrderBySortOrderAsc(itemIds),
            child -> child.getCoverageItem().getId());

    List<CoverageItemResponse> responses = new ArrayList<>();
    for (CoverageItem item : items) {
      Set<Concern> matched = matchedByItem.getOrDefault(item.getId(), Set.of());
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
              details.getOrDefault(item.getId(), List.of()).stream()
                  .map(
                      child ->
                          new DetailItemResponse(
                              child.getTitle(), child.getSubtitle(), child.isCovered()))
                  .toList(),
              subLimits.getOrDefault(item.getId(), List.of()).stream()
                  .map(
                      child ->
                          new SubLimitResponse(
                              child.getLabel(),
                              child.getValue(),
                              child.getDescription(),
                              child.getLimitAmount(),
                              child.getLimitCurrency()))
                  .toList(),
              documents.getOrDefault(item.getId(), List.of()).stream()
                  .map(
                      child ->
                          new RequiredDocumentResponse(child.getDocumentName(), child.isMandatory()))
                  .toList(),
              exclusions.getOrDefault(item.getId(), List.of()).stream()
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

  private <T> Map<UUID, List<T>> groupBy(
      List<T> children, java.util.function.Function<T, UUID> keyMapper) {
    return children.stream().collect(Collectors.groupingBy(keyMapper));
  }
}
