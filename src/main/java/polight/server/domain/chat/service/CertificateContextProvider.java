package polight.server.domain.chat.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.analysis.entity.CoverageItem;
import polight.server.domain.analysis.repository.AnalysisResultRepository;
import polight.server.domain.analysis.repository.CoverageItemRepository;
import polight.server.domain.chat.dto.RagQueryRequest.Coverage;

/**
 * 챗봇 프롬프트에 실을 검색 범위와 가입 담보를 조립한다.
 *
 * <p>이게 오답 방지의 핵심이다. 약관에는 그 상품이 팔 수 있는 모든 특약이 실려 있어, 가입하지 않은 담보를 물어도 조항이 검색되고 "보상됩니다"라는 답이 나간다.
 * 한도 금액도 약관에는 "보험가입금액을 한도로"라고만 적혀 있어 증권에서만 얻을 수 있다.
 *
 * <p>검색 범위는 증권 분석이 매칭해 둔 약관({@code analysis_results.matched_terms_id})이다. 그것이 "이 사용자가 산 상품의
 * 약관"을 아는 유일한 경로다.
 *
 * <p>증권 분석이 아직 없거나 진행 중이면 빈 컨텍스트를 돌려준다. 그러면 {@code termsId}가 비고, 호출한 쪽이 AI를 부르지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CertificateContextProvider {

  private final AnalysisResultRepository analysisResultRepository;
  private final CoverageItemRepository coverageItemRepository;

  /**
   * @param termsId 검색할 약관. 증권 분석이 없거나 약관 매칭이 되지 않았으면 {@code null}이다
   * @param complete {@code coverages}가 증권 보장내용 표 전체인지. AI가 원본 표의 행 수를 알 수 없어 콜백으로 오지 않으므로 당분간 항상
   *     {@code false}다. 값이 켜지는 시점에 이 코드는 바뀌지 않는다
   */
  public record CertificateContext(UUID termsId, List<Coverage> coverages, boolean complete) {

    static CertificateContext empty() {
      return new CertificateContext(null, List.of(), false);
    }

    /** 검색할 약관이 정해졌는지. 아니면 AI를 부를 수 없다. */
    public boolean hasTerms() {
      return termsId != null;
    }
  }

  public CertificateContext load(UUID userId, UUID tripId) {
    List<AnalysisResult> analyses =
        analysisResultRepository.findCompletedCertificateAnalyses(userId, tripId);
    if (analyses.isEmpty()) {
      return CertificateContext.empty();
    }

    // 같은 여행에 증권을 여러 번 올릴 수 있다. 최근 분석이 현재 가입 상태다.
    AnalysisResult latest = analyses.get(0);

    List<Coverage> coverages =
        coverageItemRepository.findByAnalysisResultIdOrderBySortOrderAsc(latest.getId()).stream()
            .map(CertificateContextProvider::toCoverage)
            .toList();

    // 약관 매칭에 실패한 분석은 termsId 가 비어 나간다. 그 경우 담보는 실어 보내도 검색할 약관이 없다.
    UUID termsId = latest.hasMatchedTerms() ? latest.getMatchedTerms().getId() : null;

    return new CertificateContext(termsId, coverages, latest.isCoveragesComplete());
  }

  /**
   * 담보를 AI가 읽을 형태로 줄인다.
   *
   * <p>{@code subtitle}·{@code conditions}·면책 조항은 보내지 않는다. AI 쪽 스키마에 필드가 없고, 판단에 필요한 것은 담보명·가입여부·한도
   * 세 가지다.
   */
  private static Coverage toCoverage(CoverageItem item) {
    return new Coverage(
        item.getTitle(), item.isCovered(), item.getLimitAmount(), item.getLimitCurrency());
  }
}
