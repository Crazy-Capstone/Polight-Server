package polight.server.domain.chat.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.chat.dto.CertificateStatus;
import polight.server.domain.insurance.entity.DocumentKind;
import polight.server.domain.insurance.entity.DocumentParseStatus;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.insurance.repository.PolicyDocumentRepository;
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
 * <p>증권 분석이 아직 없거나 진행 중이면 빈 컨텍스트를 돌려준다. AI 요청용 상태는 별도로 계산하며, 약관 없는 요청의 AI 호출 여부는 호출 서비스에서 결정한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CertificateContextProvider {

  private final PolicyDocumentRepository policyDocumentRepository;
  private final AnalysisResultRepository analysisResultRepository;
  private final CoverageItemRepository coverageItemRepository;

  /**
   * @param termsId 검색할 약관. 증권 분석이 없거나 약관 매칭이 되지 않았으면 {@code null}이다
   * @param complete {@code coverages}가 증권 보장내용 표 전체인지. AI가 원본 표의 행 수를 알 수 없어 콜백으로 오지 않으므로 당분간 항상
   *     {@code false}다. 값이 켜지는 시점에 이 코드는 바뀌지 않는다
   */
  public record CertificateContext(
      UUID termsId, List<Coverage> coverages, boolean complete, CertificateStatus status) {

    static CertificateContext empty(CertificateStatus status) {
      return new CertificateContext(null, List.of(), false, status);
    }

    public boolean hasTerms() {
      return termsId != null;
    }
  }

  public CertificateContext load(UUID userId, UUID tripId) {
    // termsId 유무로 미등록을 판단하지 않는다. 약관 문서는 증권 등록에 포함하지 않는다.
    PolicyDocument document = policyDocumentRepository
        .findFirstByUserIdAndTripIdAndDocumentKindOrderByUploadedAtDescIdDesc(
            userId, tripId, DocumentKind.CERTIFICATE).orElse(null);
    if (document == null) {
      // 증권 상태는 사용자 기준이다. 다른 여행에 증권이 있으면 미등록으로 오인하지 않는다.
      // 다른 여행의 담보와 약관은 현재 여행 답변에 섞지 않는다.
      if (tripId != null) {
        return policyDocumentRepository.findFirstByUserIdAndDocumentKindOrderByUploadedAtDescIdDesc(
                userId, DocumentKind.CERTIFICATE)
            .map(registered -> CertificateContext.empty(statusOf(registered)))
            .orElseGet(() -> CertificateContext.empty(CertificateStatus.UNREGISTERED));
      }
      return CertificateContext.empty(CertificateStatus.UNREGISTERED);
    }
    AnalysisResult latest = analysisResultRepository.findOneByDocumentId(document.getId()).orElse(null);
    if (latest == null) {
      return CertificateContext.empty(document.getParseStatus() == DocumentParseStatus.FAILED
          ? CertificateStatus.FAILED : CertificateStatus.PROCESSING);
    }
    CertificateStatus status = CertificateStatus.valueOf(latest.getStatus().name());
    if (status != CertificateStatus.COMPLETED) {
      return CertificateContext.empty(status);
    }
    List<Coverage> coverages =
        coverageItemRepository.findByAnalysisResultIdOrderBySortOrderAsc(latest.getId()).stream()
            .map(CertificateContextProvider::toCoverage)
            .toList();
    UUID termsId = latest.hasMatchedTerms() ? latest.getMatchedTerms().getId() : null;
    return new CertificateContext(termsId, coverages, latest.isCoveragesComplete(), status);
  }

  private CertificateStatus statusOf(PolicyDocument document) {
    return analysisResultRepository.findOneByDocumentId(document.getId())
        .map(analysis -> CertificateStatus.valueOf(analysis.getStatus().name()))
        .orElse(document.getParseStatus() == DocumentParseStatus.FAILED
            ? CertificateStatus.FAILED : CertificateStatus.PROCESSING);
  }

  /**
   * 담보를 AI가 읽을 형태로 줄인다.
   *
   * <p>{@code conditions}를 함께 보낸다. 자기부담금과 물품당 한도가 그 문구에만 있어, 없으면 "자기부담금 얼마예요?"에 답할 수 없다.
   * 약관은 "가입금액을 한도로"까지만 적고 실제 금액은 증권에 인쇄된다.
   *
   * <p>{@code subtitle}과 면책 조항은 보내지 않는다. 면책은 약관 검색으로 나오는 것이라 프롬프트에 두 번 실을 이유가 없다.
   */
  private static Coverage toCoverage(CoverageItem item) {
    return new Coverage(
        item.getTitle(),
        item.isCovered(),
        item.getLimitAmount(),
        item.getLimitCurrency(),
        item.getConditions());
  }
}
