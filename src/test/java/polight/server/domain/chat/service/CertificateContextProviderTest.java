package polight.server.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.analysis.entity.AnalysisStatus;
import polight.server.domain.analysis.repository.AnalysisResultRepository;
import polight.server.domain.analysis.repository.CoverageItemRepository;
import polight.server.domain.chat.dto.CertificateStatus;
import polight.server.domain.insurance.entity.DocumentKind;
import polight.server.domain.insurance.entity.DocumentParseStatus;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.insurance.repository.PolicyDocumentRepository;
import polight.server.domain.terms.entity.PolicyTerms;

@ExtendWith(MockitoExtension.class)
class CertificateContextProviderTest {
  @Mock private PolicyDocumentRepository policyDocumentRepository;
  @Mock private AnalysisResultRepository analysisResultRepository;
  @Mock private CoverageItemRepository coverageItemRepository;
  @InjectMocks private CertificateContextProvider provider;
  private final UUID userId = UUID.randomUUID();
  private final UUID tripId = UUID.randomUUID();
  private final UUID documentId = UUID.randomUUID();

  @Test
  void noCertificateIsUnregistered() {
    given(policyDocumentRepository.findFirstByUserIdAndTripIdAndDocumentKindOrderByUploadedAtDescIdDesc(
        userId, tripId, DocumentKind.CERTIFICATE)).willReturn(Optional.empty());
    var context = provider.load(userId, tripId);
    assertThat(context.status()).isEqualTo(CertificateStatus.UNREGISTERED);
    assertThat(context.coverages()).isEmpty();
    assertThat(context.hasTerms()).isFalse();
    verify(analysisResultRepository, never()).findOneByDocumentId(any());
  }

  @Test
  void registeredCertificateInAnotherTripDoesNotBecomeUnregisteredOrLeakTerms() {
    var document = PolicyDocument.builder().build();
    ReflectionTestUtils.setField(document, "id", documentId);
    given(policyDocumentRepository.findFirstByUserIdAndDocumentKindOrderByUploadedAtDescIdDesc(
        userId, DocumentKind.CERTIFICATE)).willReturn(Optional.of(document));
    given(analysisResultRepository.findOneByDocumentId(documentId))
        .willReturn(Optional.of(AnalysisResult.builder().status(AnalysisStatus.COMPLETED).build()));
    var context = provider.load(userId, tripId);
    assertThat(context.status()).isEqualTo(CertificateStatus.COMPLETED);
    assertThat(context.termsId()).isNull();
    assertThat(context.coverages()).isEmpty();
  }

  @Test
  void uploadedCertificateWithoutAnalysisIsProcessing() {
    document(DocumentParseStatus.UPLOADED);
    assertThat(provider.load(userId, tripId).status()).isEqualTo(CertificateStatus.PROCESSING);
  }

  @Test
  void parseFailureWithoutAnalysisIsFailed() {
    document(DocumentParseStatus.FAILED);
    assertThat(provider.load(userId, tripId).status()).isEqualTo(CertificateStatus.FAILED);
  }

  @Test
  void processingAnalysisIsNotUnregistered() {
    analysis(AnalysisStatus.PROCESSING);
    assertThat(provider.load(userId, tripId).status()).isEqualTo(CertificateStatus.PROCESSING);
  }

  @Test
  void failedAnalysisIsNotUnregistered() {
    analysis(AnalysisStatus.FAILED);
    assertThat(provider.load(userId, tripId).status()).isEqualTo(CertificateStatus.FAILED);
    verify(coverageItemRepository, never()).findByAnalysisResultIdOrderBySortOrderAsc(any());
  }

  @Test
  void completedAnalysisWithoutTermsRemainsCompleted() {
    analysis(AnalysisStatus.COMPLETED);
    var context = provider.load(userId, tripId);
    assertThat(context.status()).isEqualTo(CertificateStatus.COMPLETED);
    assertThat(context.hasTerms()).isFalse();
  }

  @Test
  void completedAnalysisCarriesTermsAndCoverages() {
    var analysis = analysis(AnalysisStatus.COMPLETED);
    var terms = PolicyTerms.official("보험사", "상품", null, null);
    UUID termsId = UUID.randomUUID();
    ReflectionTestUtils.setField(terms, "id", termsId);
    analysis.linkTerms(terms, "EXACT", null);
    var coverage = polight.server.domain.analysis.entity.CoverageItem.builder()
        .analysisResult(analysis).title("해외의료비").covered(true)
        .limitAmount(30000000L).limitCurrency("KRW").conditions("자기부담금 10,000").build();
    given(coverageItemRepository.findByAnalysisResultIdOrderBySortOrderAsc(analysis.getId()))
        .willReturn(List.of(coverage));
    var context = provider.load(userId, tripId);
    assertThat(context.status()).isEqualTo(CertificateStatus.COMPLETED);
    assertThat(context.termsId()).isEqualTo(termsId);
    assertThat(context.coverages()).hasSize(1);
    assertThat(context.coverages().get(0).conditions()).isEqualTo("자기부담금 10,000");
    assertThat(context.coverages().get(0).limitAmount()).isEqualTo(30000000L);
  }

  private void document(DocumentParseStatus status) {
    var document = PolicyDocument.builder().parseStatus(status).build();
    ReflectionTestUtils.setField(document, "id", documentId);
    given(policyDocumentRepository.findFirstByUserIdAndTripIdAndDocumentKindOrderByUploadedAtDescIdDesc(
        userId, tripId, DocumentKind.CERTIFICATE)).willReturn(Optional.of(document));
  }

  private AnalysisResult analysis(AnalysisStatus status) {
    document(DocumentParseStatus.UPLOADED);
    var analysis = AnalysisResult.builder().status(status).build();
    ReflectionTestUtils.setField(analysis, "id", UUID.randomUUID());
    given(analysisResultRepository.findOneByDocumentId(documentId)).willReturn(Optional.of(analysis));
    return analysis;
  }
}
