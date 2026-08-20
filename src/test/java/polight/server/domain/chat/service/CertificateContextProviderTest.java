package polight.server.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.analysis.entity.CoverageItem;
import polight.server.domain.analysis.entity.CoverageStatus;
import polight.server.domain.analysis.repository.AnalysisResultRepository;
import polight.server.domain.analysis.repository.CoverageItemRepository;
import polight.server.domain.chat.dto.RagQueryRequest.Coverage;
import polight.server.domain.chat.service.CertificateContextProvider.CertificateContext;

@ExtendWith(MockitoExtension.class)
class CertificateContextProviderTest {

  @Mock private AnalysisResultRepository analysisResultRepository;
  @Mock private CoverageItemRepository coverageItemRepository;

  @InjectMocks private CertificateContextProvider provider;

  private final UUID userId = UUID.randomUUID();
  private final UUID tripId = UUID.randomUUID();

  @Test
  void returnsEmptyContextWhenNoCertificateAnalysis() {
    given(analysisResultRepository.findCompletedCertificateAnalyses(userId, tripId))
        .willReturn(List.of());

    CertificateContext context = provider.load(userId, tripId);

    // 증권 분석이 없으면 AI는 약관만 보고 답한다. 질문 자체를 막지는 않는다.
    assertThat(context.coverages()).isEmpty();
    assertThat(context.complete()).isFalse();
  }

  @Test
  void mapsCoverageNameSubscriptionAndLimit() {
    AnalysisResult analysis = analysis();
    given(analysisResultRepository.findCompletedCertificateAnalyses(userId, tripId))
        .willReturn(List.of(analysis));
    given(coverageItemRepository.findByAnalysisResultIdOrderBySortOrderAsc(analysis.getId()))
        .willReturn(
            List.of(
                coverageItem(analysis, "해외의료비", CoverageStatus.COVERED, true, 30_000_000L),
                coverageItem(analysis, "골프용품손해", CoverageStatus.NOT_COVERED, false, null)));

    List<Coverage> coverages = provider.load(userId, tripId).coverages();

    assertThat(coverages)
        .extracting(Coverage::name, Coverage::subscribed, Coverage::limitAmount)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("해외의료비", true, 30_000_000L),
            org.assertj.core.groups.Tuple.tuple("골프용품손해", false, null));
    assertThat(coverages.get(0).limitCurrency()).isEqualTo("KRW");
  }

  @Test
  void usesMostRecentCertificateAnalysis() {
    AnalysisResult latest = analysis();
    AnalysisResult older = analysis();
    // 리포지토리가 completedAt 내림차순으로 돌려준다. 최근 분석이 현재 가입 상태다.
    given(analysisResultRepository.findCompletedCertificateAnalyses(userId, tripId))
        .willReturn(List.of(latest, older));
    given(coverageItemRepository.findByAnalysisResultIdOrderBySortOrderAsc(latest.getId()))
        .willReturn(List.of(coverageItem(latest, "해외의료비", CoverageStatus.COVERED, true, 1_000L)));

    assertThat(provider.load(userId, tripId).coverages()).hasSize(1);
  }

  private AnalysisResult analysis() {
    AnalysisResult analysis = AnalysisResult.builder().build();
    setField(analysis, "id", UUID.randomUUID());
    return analysis;
  }

  private CoverageItem coverageItem(
      AnalysisResult analysis,
      String title,
      CoverageStatus status,
      boolean covered,
      Long limitAmount) {
    return CoverageItem.builder()
        .analysisResult(analysis)
        .title(title)
        .coverageStatus(status)
        .covered(covered)
        .limitAmount(limitAmount)
        .limitCurrency("KRW")
        .build();
  }

  private static void setField(Object target, String name, Object value) {
    try {
      var field = target.getClass().getDeclaredField(name);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
