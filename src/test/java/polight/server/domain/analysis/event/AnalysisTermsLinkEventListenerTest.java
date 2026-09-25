package polight.server.domain.analysis.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import polight.server.domain.terms.service.AnalysisTermsRelinkResult;
import polight.server.domain.terms.service.AnalysisTermsRelinker;
import polight.server.domain.terms.service.CoverageTermsLinkSummary;

@ExtendWith(MockitoExtension.class)
class AnalysisTermsLinkEventListenerTest {

  @Mock private AnalysisTermsRelinker analysisTermsRelinker;

  @Test
  void rematchesEvenWhenTermsAreAlreadyLinked() {
    UUID analysisResultId = UUID.randomUUID();
    given(analysisTermsRelinker.relink(analysisResultId, true))
        .willReturn(new AnalysisTermsRelinkResult(analysisResultId, true, summary()));

    listener().linkTerms(new AnalysisCompletedEvent(analysisResultId));

    // 방금 보험사/상품명이 새로 채워졌다. 이전 연결은 그 전 이름으로 고른 것이라 다시 판단해야 한다.
    verify(analysisTermsRelinker).relink(analysisResultId, true);
  }

  @Test
  void swallowsFailureBecauseTheAnalysisIsAlreadyCommitted() {
    UUID analysisResultId = UUID.randomUUID();
    willThrow(new IllegalStateException("약관 조회 실패"))
        .given(analysisTermsRelinker)
        .relink(analysisResultId, true);

    // 여기서 다시 던져도 커밋된 분석을 되돌리지 못하고, AI 쪽에는 콜백 실패로 보여 재시도만 부른다.
    // 약관이 비는 것은 백필로 나중에 메울 수 있다.
    assertThatCode(() -> listener().linkTerms(new AnalysisCompletedEvent(analysisResultId)))
        .doesNotThrowAnyException();
  }

  private AnalysisTermsLinkEventListener listener() {
    return new AnalysisTermsLinkEventListener(analysisTermsRelinker);
  }

  private CoverageTermsLinkSummary summary() {
    return new CoverageTermsLinkSummary(3, 2, 0, 0, java.util.List.of());
  }
}
