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
import polight.server.domain.chat.dto.CertificateStatus;
import polight.server.domain.chat.dto.RagQueryRequest.Coverage;
import polight.server.domain.insurance.entity.DocumentKind;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.insurance.repository.PolicyDocumentRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CertificateContextProvider {

  private final PolicyDocumentRepository policyDocumentRepository;
  private final AnalysisResultRepository analysisResultRepository;
  private final CoverageItemRepository coverageItemRepository;

  public record CertificateContext(
      UUID termsId, List<Coverage> coverages, boolean complete, CertificateStatus certificateStatus) {

    static CertificateContext empty(CertificateStatus status) {
      return new CertificateContext(null, List.of(), false, status);
    }
  }

  public CertificateContext load(UUID userId, UUID tripId) {
    PolicyDocument latest = policyDocumentRepository
        .findFirstByUserIdAndTripIdAndDocumentKindOrderByUploadedAtDescIdDesc(
            userId, tripId, DocumentKind.CERTIFICATE)
        .orElse(null);
    if (latest == null) {
      return CertificateContext.empty(CertificateStatus.UNREGISTERED);
    }

    AnalysisResult analysis = analysisResultRepository.findOneByDocumentId(latest.getId()).orElse(null);
    CertificateStatus status = statusOf(latest, analysis);
    if (status != CertificateStatus.COMPLETED || analysis == null) {
      return CertificateContext.empty(status);
    }

    List<Coverage> coverages = coverageItemRepository
        .findByAnalysisResultIdOrderBySortOrderAsc(analysis.getId()).stream()
        .map(CertificateContextProvider::toCoverage)
        .toList();
    UUID termsId = analysis.hasMatchedTerms() ? analysis.getMatchedTerms().getId() : null;
    return new CertificateContext(termsId, coverages, analysis.isCoveragesComplete(), status);
  }

  private CertificateStatus statusOf(PolicyDocument document, AnalysisResult analysis) {
    if (analysis != null) {
      return switch (analysis.getStatus()) {
        case PROCESSING -> CertificateStatus.ANALYZING;
        case FAILED -> CertificateStatus.FAILED;
        case COMPLETED -> CertificateStatus.COMPLETED;
      };
    }
    return switch (document.getParseStatus()) {
      case UPLOADED, PROCESSING -> CertificateStatus.ANALYZING;
      case FAILED -> CertificateStatus.FAILED;
      case COMPLETED -> CertificateStatus.COMPLETED;
    };
  }

  private static Coverage toCoverage(CoverageItem item) {
    return new Coverage(
        item.getTitle(),
        item.isCovered(),
        item.getLimitAmount(),
        item.getLimitCurrency(),
        item.getConditions());
  }
}
