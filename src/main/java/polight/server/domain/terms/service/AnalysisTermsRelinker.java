package polight.server.domain.terms.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.analysis.entity.CoverageItem;
import polight.server.domain.analysis.repository.AnalysisResultRepository;
import polight.server.domain.analysis.repository.CoverageItemRepository;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

/**
 * 이미 끝난 분석 하나에 약관과 보장 규칙을 다시 붙인다.
 *
 * <p>{@code AnalysisCallbackService}가 콜백 안에서 하는 일과 같은 두 단계다 -- 증권을 약관에 붙이고, 그 약관의 규칙을 담보에 붙인다.
 * 다른 점은 시점뿐이다. 저쪽은 분석이 막 끝난 순간이고, 여기는 한참 뒤다.
 *
 * <p>{@link TermsBackfillService}에서 떼어 둔 이유는 트랜잭션 경계 때문이다. 백필은 분석 하나가 실패해도 나머지를 계속 붙여야 하므로 건마다
 * 트랜잭션이 따로 열려야 하는데, 같은 빈 안에서 부르면 프록시를 타지 않아 {@code @Transactional}이 걸리지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AnalysisTermsRelinker {

  private final AnalysisResultRepository analysisResultRepository;
  private final CoverageItemRepository coverageItemRepository;
  private final PolicyTermsMatchingService policyTermsMatchingService;
  private final CoverageTermsLinker coverageTermsLinker;

  /**
   * 분석 한 건을 다시 연결한다.
   *
   * <p>{@link Propagation#REQUIRES_NEW}로 자기 트랜잭션을 연다. 호출부가 트랜잭션을 갖고 있더라도 이 한 건의 실패가 앞서 붙인 것들을
   * 되돌리지 않게 하기 위해서다.
   *
   * @param rematch 이미 약관이 붙어 있는 분석도 다시 판단할지. {@code false}면 붙어 있는 연결은 그대로 두고 담보 규칙만 다시 붙인다
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public AnalysisTermsRelinkResult relink(UUID analysisResultId, boolean rematch) {
    AnalysisResult analysis =
        analysisResultRepository
            .findById(analysisResultId)
            .orElseThrow(() -> new BaseException(ErrorCode.ANALYSIS_RESULT_NOT_FOUND));

    if (rematch || !analysis.hasMatchedTerms()) {
      policyTermsMatchingService.matchAndLink(analysis);
    }

    List<CoverageItem> items =
        coverageItemRepository.findByAnalysisResultIdOrderBySortOrderAsc(analysisResultId);
    CoverageTermsLinkSummary linkSummary = coverageTermsLinker.link(analysis, items);

    return new AnalysisTermsRelinkResult(
        analysisResultId, analysis.hasMatchedTerms(), linkSummary);
  }
}
