package polight.server.domain.analysis.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.analysis.dto.AnalysisCallbackRequest;
import polight.server.domain.analysis.dto.AnalysisCallbackRequest.CoverageItemPayload;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.analysis.entity.CoverageItem;
import polight.server.domain.analysis.entity.CoverageStatus;
import polight.server.domain.analysis.event.AnalysisCompletedEvent;
import polight.server.domain.analysis.repository.AnalysisResultRepository;
import polight.server.domain.analysis.repository.CoverageItemRepository;
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
 * <p>담보를 저장한 뒤 <b>약관 연결</b>까지 이어지지만, 그 단계는 이 트랜잭션 안에서 하지 않는다. 커밋 후
 * {@link polight.server.domain.analysis.event.AnalysisTermsLinkEventListener}가 이어받는다 -- 이유는 그쪽에 적어 두었다.
 * 재수신해도 같은 이름으로 같은 약관을 다시 찾으므로 그 단계도 멱등하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisCallbackService {

  private final AnalysisResultRepository analysisResultRepository;
  private final CoverageItemRepository coverageItemRepository;
  private final ApplicationEventPublisher eventPublisher;

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

    List<CoverageItem> savedItems = replaceCoverageItems(result, request.coverageItems());

    result.completeWith(
        request.summary(),
        rawPayload,
        request.embeddingModel(),
        request.embeddingDimension(),
        request.accuracyScore(),
        Boolean.TRUE.equals(request.coveragesComplete()),
        request.insurerName(),
        request.productName(),
        request.startDate(),
        request.endDate(),
        LocalDateTime.now());
    result.getDocument().markParseCompleted();

    // 보험사/상품명이 방금 채워졌으니 이제 약관을 찾을 수 있다. 다만 이 트랜잭션에서는 하지
    // 않는다 -- 곧 AI 서버 호출로 바뀔 자리라, DB 커넥션을 쥔 채 HTTP 를 기다리게 된다.
    // 커밋 뒤에 AnalysisTermsLinkEventListener 가 이어받는다.
    eventPublisher.publishEvent(new AnalysisCompletedEvent(analysisResultId));

    log.info(
        "분석 완료 콜백 반영: analysisResultId={}, 담보 {}건 (약관 연결은 커밋 후 진행)",
        analysisResultId,
        savedItems.size());
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

  /** @return 저장된 담보. 로그에 건수를 남기려고 돌려준다 -- 약관 규칙 연결은 커밋 후 리스너가 다시 읽는다 */
  private List<CoverageItem> replaceCoverageItems(
      AnalysisResult result, List<CoverageItemPayload> payloads) {
    deleteCoverageItems(result.getId());

    List<CoverageItemPayload> items = payloads == null ? List.of() : payloads;
    List<CoverageItem> saved = new ArrayList<>(items.size());
    for (int index = 0; index < items.size(); index++) {
      CoverageItemPayload payload = items.get(index);
      saved.add(coverageItemRepository.save(toCoverageItem(result, payload, index)));
      warnOnDroppedTermsFacts(payload, index);
    }
    return saved;
  }

  /**
   * 콜백에 약관에서 나온 값이 실려 오면 남긴다.
   *
   * <p>면책·청구서류·세부한도·세부항목은 이제 담보가 아니라 약관의 보장 규칙({@code policy_terms_coverages})에 달린다. 상품 공용
   * 사실이라 가입자마다 복제할 것이 아니기 때문이다. 그래서 담보 콜백으로 온 이 값들은 저장할 자리가 없다.
   *
   * <p>증권 분석은 원래 이 값들을 보내지 않는다 — 증권에 그 정보가 없다. 실려 온다면 약관 분석 결과가 증권 콜백 경로로 흘러들어온 것이므로, 조용히
   * 버리지 않고 남겨서 AI 쪽 계약이 어긋났다는 사실이 드러나게 한다.
   */
  private void warnOnDroppedTermsFacts(CoverageItemPayload payload, int index) {
    int dropped =
        size(payload.detailItems())
            + size(payload.subLimits())
            + size(payload.requiredDocuments())
            + size(payload.exclusions());
    if (dropped == 0) {
      return;
    }

    log.warn(
        "담보 콜백에 약관에서 나온 값이 {}건 실려 있어 저장하지 않았습니다: coverageItems[{}].title={}. "
        + "면책·청구서류·세부한도는 policy_terms_coverages 에 적재해야 합니다.",
        dropped,
        index,
        payload.title());
  }

  private int size(List<?> values) {
    return values == null ? 0 : values.size();
  }

  /**
   * 이 분석에 딸린 담보를 전부 지운다.
   *
   * <p>딸린 자식이 없어 담보만 지우면 된다. 면책·청구서류·세부한도·근거조항은 약관의 보장 규칙에 달려 있고, 그것은 상품 공용 데이터라 한 사용자의
   * 재분석으로 지워서는 안 된다.
   *
   * <p>{@code flush}하는 이유: Hibernate는 한 트랜잭션에서 INSERT를 DELETE보다 먼저 내보낸다. 지울 행과 새로 넣는 행의 id가 달라
   * 지금은 어느 순서든 성공하지만, 삭제를 먼저 확정해 두면 나중에 유니크 제약이 붙어도 이 함수를 다시 보지 않아도 된다.
   */
  private void deleteCoverageItems(UUID analysisResultId) {
    List<CoverageItem> existing =
        coverageItemRepository.findByAnalysisResultIdOrderBySortOrderAsc(analysisResultId);
    if (existing.isEmpty()) {
      return;
    }

    coverageItemRepository.deleteAll(existing);
    coverageItemRepository.flush();

    log.info("재수신으로 기존 담보 {}건을 지우고 다시 저장합니다: analysisResultId={}", existing.size(), analysisResultId);
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





  /**
   * enum 컬럼에는 DB CHECK 제약이 걸려 있어, 허용값이 아니면 저장 단계에서 제약 위반으로 터진다. 그러면 어느 필드가 문제였는지 알기 어렵다. 여기서 먼저
   * 걸러 로그에 값을 남긴다.
   *
   * <p>{@code null}은 허용한다 — 엔티티 기본값({@code NOT_COVERED})이 쓰인다.
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
