package polight.server.domain.analysis.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.analysis.dto.AnalysisCallbackRequest;
import polight.server.domain.analysis.dto.AnalysisCallbackRequest.CoverageItemPayload;
import polight.server.domain.analysis.dto.AnalysisCallbackRequest.DetailItemPayload;
import polight.server.domain.analysis.dto.AnalysisCallbackRequest.ExclusionPayload;
import polight.server.domain.analysis.dto.AnalysisCallbackRequest.RequiredDocumentPayload;
import polight.server.domain.analysis.dto.AnalysisCallbackRequest.SubLimitPayload;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.analysis.entity.CoverageDetailItem;
import polight.server.domain.analysis.entity.CoverageItem;
import polight.server.domain.analysis.entity.CoverageStatus;
import polight.server.domain.analysis.entity.ExclusionCondition;
import polight.server.domain.analysis.entity.ExclusionConditionSeverity;
import polight.server.domain.analysis.entity.RequiredDocument;
import polight.server.domain.analysis.entity.SubCoverageLimit;
import polight.server.domain.analysis.repository.AnalysisResultRepository;
import polight.server.domain.analysis.repository.CoverageDetailItemRepository;
import polight.server.domain.analysis.repository.CoverageItemRepository;
import polight.server.domain.analysis.repository.ExclusionConditionRepository;
import polight.server.domain.analysis.repository.RequiredDocumentRepository;
import polight.server.domain.analysis.repository.SubCoverageLimitRepository;
import polight.server.domain.rag.repository.CoverageItemSourceRepository;
import polight.server.domain.terms.service.PolicyTermsMatchingService;
import polight.server.domain.terms.service.TermsMatch;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

/**
 * AI 서버가 보내는 분석 결과 콜백을 받아 저장한다.
 *
 * <p><b>멱등하다.</b> AI 서버는 콜백 실패 시 지수 백오프로 3회까지 재시도하므로 같은 본문이 여러 번 도착할 수 있다. 매번 기존 담보 트리를 지우고 다시 넣기
 * 때문에 몇 번 받아도 결과가 같다. 부분 저장 상태로 남는 경우도 없다.
 *
 * <p>AI가 보내지 않는 두 값을 여기서 만든다.
 *
 * <ul>
 *   <li>{@code is_covered} — {@code coverage_status}에서 파생한다
 *   <li>{@code sort_order} — 배열 순서로 부여한다. AI는 배열을 화면에 보여줄 순서(중요도 순)로만 정렬해 보낸다
 * </ul>
 *
 * <p>완료 콜백은 담보를 저장한 뒤 <b>약관 연결</b>까지 한다. AI는 "어느 약관인가"를 보내지 않으므로, 콜백으로 받은 보험사/상품명으로 백엔드가
 * {@code policy_terms}를 찾는다({@link PolicyTermsMatchingService}). 재수신해도 같은 이름으로 같은 약관을 다시 찾으므로
 * 이 단계도 멱등하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisCallbackService {

  private final AnalysisResultRepository analysisResultRepository;
  private final CoverageItemRepository coverageItemRepository;
  private final CoverageDetailItemRepository coverageDetailItemRepository;
  private final SubCoverageLimitRepository subCoverageLimitRepository;
  private final RequiredDocumentRepository requiredDocumentRepository;
  private final ExclusionConditionRepository exclusionConditionRepository;
  private final CoverageItemSourceRepository coverageItemSourceRepository;
  private final PolicyTermsMatchingService policyTermsMatchingService;

  /**
   * 분석 완료 콜백.
   *
   * @param rawPayload 받은 본문 전체. 아직 저장할 컬럼이 없는 필드를 잃지 않기 위해 그대로 보관한다
   */
  @Transactional
  public void complete(
      UUID analysisResultId, AnalysisCallbackRequest request, String rawPayload) {
    AnalysisResult result = getAnalysisResult(analysisResultId);
    warnOnIdMismatch(analysisResultId, request.analysisResultId());

    replaceCoverageItems(result, request.coverageItems());

    result.completeWith(
        request.summary(),
        rawPayload,
        request.embeddingModel(),
        request.embeddingDimension(),
        request.accuracyScore(),
        Boolean.TRUE.equals(request.coveragesComplete()),
        request.insurerName(),
        request.productName(),
        LocalDateTime.now());
    result.getDocument().markParseCompleted();

    // 보험사/상품명이 방금 채워졌으니 여기서 바로 약관을 찾는다.
    //
    // 같은 트랜잭션 안에서 하는 이유: 연결이 나중에 따로 서면, 그 사이에 조회한 분석 결과는
    // 완료 상태인데 약관만 비어 있다. 프론트는 그것을 "약관 없음"으로 보고 사용자에게 약관
    // 업로드를 요청하게 된다 -- 잠시 뒤면 붙을 약관인데도.
    //
    // 매칭은 후보 목록을 한 번 읽어 메모리에서 비교하는 것이 전부라 콜백 응답을 늦추지 않는다.
    TermsMatch termsMatch = policyTermsMatchingService.matchAndLink(result);

    log.info(
        "분석 완료 콜백 반영: analysisResultId={}, 담보 {}건, 약관 매칭={}",
        analysisResultId,
        request.coverageItems() == null ? 0 : request.coverageItems().size(),
        termsMatch.stage());
  }

  /** 분석 실패 콜백. 담보 트리는 건드리지 않는다. 실패 전에 저장된 것이 있으면 그대로 남는다. */
  @Transactional
  public void fail(UUID analysisResultId, String errorMessage) {
    AnalysisResult result = getAnalysisResult(analysisResultId);

    result.markFailed(errorMessage, LocalDateTime.now());
    result.getDocument().markParseFailed();

    log.warn("분석 실패 콜백 반영: analysisResultId={}, 사유={}", analysisResultId, errorMessage);
  }

  private AnalysisResult getAnalysisResult(UUID analysisResultId) {
    return analysisResultRepository
        .findById(analysisResultId)
        .orElseThrow(() -> new BaseException(ErrorCode.ANALYSIS_RESULT_NOT_FOUND));
  }

  private void replaceCoverageItems(AnalysisResult result, List<CoverageItemPayload> payloads) {
    deleteCoverageItems(result.getId());

    List<CoverageItemPayload> items = payloads == null ? List.of() : payloads;
    for (int index = 0; index < items.size(); index++) {
      CoverageItemPayload payload = items.get(index);
      CoverageItem item = coverageItemRepository.save(toCoverageItem(result, payload, index));
      saveDetailItems(item, payload.detailItems());
      saveSubLimits(item, payload.subLimits());
      saveRequiredDocuments(item, payload.requiredDocuments());
      saveExclusions(item, payload.exclusions());
    }
  }

  /**
   * 이 분석에 딸린 담보 트리를 전부 지운다.
   *
   * <p>삭제 순서가 강제된다. {@code coverage_item_sources}와 자식 4종이 담보를 FK로 참조하므로, 담보를 먼저 지우면 FK 위반이다.
   *
   * <p>마지막에 {@code flush}하는 이유: Hibernate는 한 트랜잭션에서 INSERT를 DELETE보다 먼저 내보낸다. 지울 행과 새로 넣는 행의
   * id가 달라 지금은 어느 순서든 성공하지만, 삭제를 먼저 확정해 두면 나중에 유니크 제약이 붙어도 이 함수를 다시 보지 않아도 된다.
   */
  private void deleteCoverageItems(UUID analysisResultId) {
    List<CoverageItem> existing =
        coverageItemRepository.findByAnalysisResultIdOrderBySortOrderAsc(analysisResultId);
    if (existing.isEmpty()) {
      return;
    }

    List<UUID> itemIds = existing.stream().map(CoverageItem::getId).toList();

    coverageItemSourceRepository.deleteByCoverageItemIdIn(itemIds);
    coverageDetailItemRepository.deleteByCoverageItemIdIn(itemIds);
    subCoverageLimitRepository.deleteByCoverageItemIdIn(itemIds);
    requiredDocumentRepository.deleteByCoverageItemIdIn(itemIds);
    exclusionConditionRepository.deleteByCoverageItemIdIn(itemIds);
    coverageItemRepository.deleteAll(existing);
    coverageItemRepository.flush();

    log.info("재수신으로 기존 담보 {}건을 지우고 다시 저장합니다: analysisResultId={}", itemIds.size(), analysisResultId);
  }

  private CoverageItem toCoverageItem(
      AnalysisResult result, CoverageItemPayload payload, int index) {
    CoverageStatus coverageStatus = parseCoverageStatus(payload.coverageStatus());

    return CoverageItem.builder()
        .analysisResult(result)
        .title(require(payload.title(), "coverageItems[" + index + "].title"))
        .subtitle(payload.subtitle())
        .category(payload.category())
        .limitLabel(payload.limitLabel())
        .coverageStatus(coverageStatus)
        .covered(isCovered(coverageStatus))
        .limitAmount(payload.limitAmount())
        .limitCurrency(payload.limitCurrency())
        .conditions(payload.conditions())
        .sortOrder(index)
        .build();
  }

  /**
   * {@code is_covered}는 {@code coverage_status}에서 파생한다.
   *
   * <p>{@code status == COVERED}로 계산하면 부분 보장 담보가 화면에서 "미보장"으로 표시된다. 파생 규칙을 이 한 곳에만 둔다.
   */
  private boolean isCovered(CoverageStatus coverageStatus) {
    return coverageStatus == CoverageStatus.COVERED
        || coverageStatus == CoverageStatus.PARTIALLY_COVERED;
  }

  private void saveDetailItems(CoverageItem item, List<DetailItemPayload> payloads) {
    List<DetailItemPayload> details = payloads == null ? List.of() : payloads;
    for (int index = 0; index < details.size(); index++) {
      DetailItemPayload payload = details.get(index);
      coverageDetailItemRepository.save(
          CoverageDetailItem.builder()
              .coverageItem(item)
              .title(require(payload.title(), "detailItems[" + index + "].title"))
              .subtitle(payload.subtitle())
              .covered(Boolean.TRUE.equals(payload.isCovered()))
              .sortOrder(index)
              .build());
    }
  }

  private void saveSubLimits(CoverageItem item, List<SubLimitPayload> payloads) {
    List<SubLimitPayload> subLimits = payloads == null ? List.of() : payloads;
    for (int index = 0; index < subLimits.size(); index++) {
      SubLimitPayload payload = subLimits.get(index);
      subCoverageLimitRepository.save(
          SubCoverageLimit.builder()
              .coverageItem(item)
              .label(require(payload.label(), "subLimits[" + index + "].label"))
              .value(require(payload.value(), "subLimits[" + index + "].value"))
              .description(payload.description())
              .limitAmount(payload.limitAmount())
              .limitCurrency(payload.limitCurrency())
              .sortOrder(index)
              .build());
    }
  }

  private void saveRequiredDocuments(CoverageItem item, List<RequiredDocumentPayload> payloads) {
    List<RequiredDocumentPayload> documents = payloads == null ? List.of() : payloads;
    for (int index = 0; index < documents.size(); index++) {
      RequiredDocumentPayload payload = documents.get(index);
      requiredDocumentRepository.save(
          RequiredDocument.builder()
              .coverageItem(item)
              .documentName(
                  require(payload.documentName(), "requiredDocuments[" + index + "].documentName"))
              .mandatory(Boolean.TRUE.equals(payload.isMandatory()))
              .sortOrder(index)
              .build());
    }
  }

  private void saveExclusions(CoverageItem item, List<ExclusionPayload> payloads) {
    List<ExclusionPayload> exclusions = payloads == null ? List.of() : payloads;
    for (int index = 0; index < exclusions.size(); index++) {
      ExclusionPayload payload = exclusions.get(index);
      exclusionConditionRepository.save(
          ExclusionCondition.builder()
              .coverageItem(item)
              .title(require(payload.title(), "exclusions[" + index + "].title"))
              .description(payload.description())
              .sourceText(payload.sourceText())
              .severity(parseSeverity(payload.severity()))
              .sortOrder(index)
              .build());
    }
  }

  /**
   * enum 컬럼에는 DB CHECK 제약이 걸려 있어, 허용값이 아니면 저장 단계에서 제약 위반으로 터진다. 그러면 어느 필드가 문제였는지 알기 어렵다. 여기서 먼저
   * 걸러 로그에 값을 남긴다.
   *
   * <p>{@code null}은 허용한다 — 엔티티 기본값(각각 {@code NOT_COVERED}, {@code GENERAL})이 쓰인다.
   */
  private CoverageStatus parseCoverageStatus(String value) {
    if (value == null) {
      return null;
    }
    try {
      return CoverageStatus.valueOf(value);
    } catch (IllegalArgumentException exception) {
      log.warn("콜백의 coverageStatus 값을 해석할 수 없습니다: {}", value);
      throw new BaseException(ErrorCode.INVALID_INPUT, exception);
    }
  }

  private ExclusionConditionSeverity parseSeverity(String value) {
    if (value == null) {
      return null;
    }
    try {
      return ExclusionConditionSeverity.valueOf(value);
    } catch (IllegalArgumentException exception) {
      log.warn("콜백의 severity 값을 해석할 수 없습니다: {}", value);
      throw new BaseException(ErrorCode.INVALID_INPUT, exception);
    }
  }

  /** NOT NULL 컬럼에 들어갈 값을 미리 확인한다. 없으면 어느 필드인지 로그에 남기고 400으로 돌려준다. */
  private String require(String value, String fieldPath) {
    if (value == null || value.isBlank()) {
      log.warn("콜백의 필수 필드가 비어 있습니다: {}", fieldPath);
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }
    return value;
  }

  /** 경로의 값을 신뢰하되, 본문과 다르면 남긴다. AI 쪽 버그를 조용히 넘기지 않기 위한 것이다. */
  private void warnOnIdMismatch(UUID pathId, UUID bodyId) {
    if (bodyId != null && !bodyId.equals(pathId)) {
      log.warn("콜백 경로와 본문의 analysisResultId가 다릅니다: 경로={}, 본문={}", pathId, bodyId);
    }
  }
}
