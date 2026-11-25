package polight.server.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.analysis.entity.AnalysisStatus;
import polight.server.domain.analysis.entity.CoverageItem;
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

  @Test
  void noCertificateIsUnregisteredWithoutLoadingAnyAnalysis() {
    given(policyDocumentRepository.findFirstByUserIdAndTripIdAndDocumentKindOrderByUploadedAtDescIdDesc(
        userId, tripId, DocumentKind.CERTIFICATE)).willReturn(Optional.empty());

    var context = provider.load(userId, tripId);

    assertEmpty(context, CertificateStatus.UNREGISTERED);
    verifyNoInteractions(analysisResultRepository, coverageItemRepository);
  }

  @ParameterizedTest
  @EnumSource(DocumentParseStatus.class)
  void fallsBackToDocumentStatusWhenAnalysisDoesNotExist(DocumentParseStatus parseStatus) {
    PolicyDocument document = document(parseStatus);
    given(analysisResultRepository.findOneByDocumentId(document.getId())).willReturn(Optional.empty());

    var expected = switch (parseStatus) {
      case UPLOADED, PROCESSING -> CertificateStatus.ANALYZING;
      case FAILED -> CertificateStatus.FAILED;
      case COMPLETED -> CertificateStatus.COMPLETED;
    };
    assertEmpty(provider.load(userId, tripId), expected);
    verifyNoInteractions(coverageItemRepository);
  }

  @ParameterizedTest
  @EnumSource(value = AnalysisStatus.class, names = {"PROCESSING", "FAILED"})
  void analysisStatusWinsOverStaleCompletedDocument(AnalysisStatus status) {
    PolicyDocument document = document(DocumentParseStatus.COMPLETED);
    AnalysisResult analysis = analysis(document, status);
    given(analysisResultRepository.findOneByDocumentId(document.getId())).willReturn(Optional.of(analysis));

    assertEmpty(provider.load(userId, tripId),
        status == AnalysisStatus.PROCESSING ? CertificateStatus.ANALYZING : CertificateStatus.FAILED);
    verifyNoInteractions(coverageItemRepository);
    verify(analysisResultRepository, never()).findCompletedCertificateAnalyses(any(), any());
  }

  @Test
  void completedAnalysisWinsAndCarriesTermsCoveragesConditionsAndCompleteness() {
    PolicyDocument document = document(DocumentParseStatus.UPLOADED);
    AnalysisResult analysis = analysis(document, AnalysisStatus.COMPLETED);
    PolicyTerms terms = PolicyTerms.official("Insurer", "Product", null, null);
    ReflectionTestUtils.setField(terms, "id", UUID.randomUUID());
    analysis.linkTerms(terms, "EXACT", null);
    ReflectionTestUtils.setField(analysis, "coveragesComplete", true);
    given(analysisResultRepository.findOneByDocumentId(document.getId())).willReturn(Optional.of(analysis));
    given(coverageItemRepository.findByAnalysisResultIdOrderBySortOrderAsc(analysis.getId()))
        .willReturn(List.of(CoverageItem.builder().analysisResult(analysis)
            .title("Medical").covered(true).limitAmount(3000L).limitCurrency("USD")
            .conditions("Deductible USD 100").build()));

    var context = provider.load(userId, tripId);

    assertThat(context.certificateStatus()).isEqualTo(CertificateStatus.COMPLETED);
    assertThat(context.termsId()).isEqualTo(terms.getId());
    assertThat(context.complete()).isTrue();
    assertThat(context.coverages()).singleElement().satisfies(coverage -> {
      assertThat(coverage.name()).isEqualTo("Medical");
      assertThat(coverage.subscribed()).isTrue();
      assertThat(coverage.limitAmount()).isEqualTo(3000L);
      assertThat(coverage.limitCurrency()).isEqualTo("USD");
      assertThat(coverage.conditions()).isEqualTo("Deductible USD 100");
    });
  }

  @Test
  void completedWithoutMatchedTermsIsNotUnregisteredAndKeepsCertificateCoverages() {
    PolicyDocument document = document(DocumentParseStatus.COMPLETED);
    AnalysisResult analysis = analysis(document, AnalysisStatus.COMPLETED);
    given(analysisResultRepository.findOneByDocumentId(document.getId())).willReturn(Optional.of(analysis));
    given(coverageItemRepository.findByAnalysisResultIdOrderBySortOrderAsc(analysis.getId()))
        .willReturn(List.of(CoverageItem.builder().analysisResult(analysis)
            .title("Medical").limitAmount(3000L).build()));

    var context = provider.load(userId, tripId);

    assertThat(context.certificateStatus()).isEqualTo(CertificateStatus.COMPLETED);
    assertThat(context.termsId()).isNull();
    assertThat(context.coverages()).hasSize(1);
    assertThat(context.complete()).isFalse();
  }

  private PolicyDocument document(DocumentParseStatus status) {
    var document = PolicyDocument.builder().parseStatus(status).documentKind(DocumentKind.CERTIFICATE)
        .uploadedAt(LocalDateTime.now()).build();
    ReflectionTestUtils.setField(document, "id", UUID.randomUUID());
    given(policyDocumentRepository.findFirstByUserIdAndTripIdAndDocumentKindOrderByUploadedAtDescIdDesc(
        userId, tripId, DocumentKind.CERTIFICATE)).willReturn(Optional.of(document));
    return document;
  }

  private AnalysisResult analysis(PolicyDocument document, AnalysisStatus status) {
    var analysis = AnalysisResult.builder().document(document).build();
    ReflectionTestUtils.setField(analysis, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(analysis, "status", status);
    return analysis;
  }

  private void assertEmpty(CertificateContextProvider.CertificateContext context, CertificateStatus status) {
    assertThat(context.certificateStatus()).isEqualTo(status);
    assertThat(context.termsId()).isNull();
    assertThat(context.coverages()).isEmpty();
    assertThat(context.complete()).isFalse();
  }
}
