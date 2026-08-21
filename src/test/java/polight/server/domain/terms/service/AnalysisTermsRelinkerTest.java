package polight.server.domain.terms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.analysis.repository.AnalysisResultRepository;
import polight.server.domain.analysis.repository.CoverageItemRepository;
import polight.server.domain.terms.entity.PolicyTerms;
import polight.server.domain.terms.entity.TermsVerificationStatus;
import polight.server.global.exception.BaseException;

@ExtendWith(MockitoExtension.class)
class AnalysisTermsRelinkerTest {

  @Mock private AnalysisResultRepository analysisResultRepository;
  @Mock private CoverageItemRepository coverageItemRepository;
  @Mock private PolicyTermsMatchingService policyTermsMatchingService;
  @Mock private CoverageTermsLinker coverageTermsLinker;

  @InjectMocks private AnalysisTermsRelinker relinker;

  private final UUID analysisResultId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    // 분석을 찾지 못하는 경우에는 링커까지 가지 않는다.
    lenient()
        .when(coverageTermsLinker.link(any(), anyList()))
        .thenReturn(CoverageTermsLinkSummary.empty());
  }

  @Test
  void matchesTermsWhenAnalysisHasNone() {
    AnalysisResult analysis = analysis();
    given(analysisResultRepository.findById(analysisResultId)).willReturn(Optional.of(analysis));
    given(coverageItemRepository.findByAnalysisResultIdOrderBySortOrderAsc(analysisResultId))
        .willReturn(List.of());

    relinker.relink(analysisResultId, false);

    // 약관 저장소가 생기기 전에 끝난 분석이다. 이것을 붙이는 것이 백필의 목적이다.
    verify(policyTermsMatchingService).matchAndLink(analysis);
  }

  @Test
  void keepsExistingTermsWhenNotRematching() {
    AnalysisResult analysis = analysis();
    analysis.linkTerms(terms());
    given(analysisResultRepository.findById(analysisResultId)).willReturn(Optional.of(analysis));
    given(coverageItemRepository.findByAnalysisResultIdOrderBySortOrderAsc(analysisResultId))
        .willReturn(List.of());

    AnalysisTermsRelinkResult result = relinker.relink(analysisResultId, false);

    // 이미 선 판단을 다시 뒤집지 않는다. 담보 규칙은 그래도 다시 붙인다.
    verify(policyTermsMatchingService, never()).matchAndLink(any());
    verify(coverageTermsLinker).link(analysis, List.of());
    assertThat(result.termsMatched()).isTrue();
  }

  @Test
  void rematchesExistingTermsWhenAsked() {
    AnalysisResult analysis = analysis();
    analysis.linkTerms(terms());
    given(analysisResultRepository.findById(analysisResultId)).willReturn(Optional.of(analysis));
    given(coverageItemRepository.findByAnalysisResultIdOrderBySortOrderAsc(analysisResultId))
        .willReturn(List.of());

    relinker.relink(analysisResultId, true);

    // 약관을 새로 적재했거나 매칭 규칙을 고친 뒤 쓰는 경로다.
    verify(policyTermsMatchingService).matchAndLink(analysis);
  }

  @Test
  void failsWhenAnalysisIsGone() {
    given(analysisResultRepository.findById(analysisResultId)).willReturn(Optional.empty());

    // 백필 서비스가 이 예외를 잡아 그 건만 건너뛴다.
    assertThatThrownBy(() -> relinker.relink(analysisResultId, false))
        .isInstanceOf(BaseException.class);
  }

  private AnalysisResult analysis() {
    AnalysisResult analysis = AnalysisResult.builder().build();
    setField(analysis, "id", analysisResultId);
    return analysis;
  }

  private PolicyTerms terms() {
    PolicyTerms terms =
        PolicyTerms.builder()
            .insurerName("메리츠화재")
            .productName("해외여행보험")
            .verificationStatus(TermsVerificationStatus.VERIFIED)
            .build();
    setField(terms, "id", UUID.randomUUID());
    return terms;
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
