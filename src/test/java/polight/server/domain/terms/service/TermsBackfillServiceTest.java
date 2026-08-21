package polight.server.domain.terms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import polight.server.domain.analysis.repository.AnalysisResultRepository;

@ExtendWith(MockitoExtension.class)
class TermsBackfillServiceTest {

  @Mock private AnalysisResultRepository analysisResultRepository;
  @Mock private AnalysisTermsRelinker analysisTermsRelinker;

  @InjectMocks private TermsBackfillService service;

  @Test
  void targetsOnlyAnalysesWithoutTermsByDefault() {
    UUID id = UUID.randomUUID();
    given(analysisResultRepository.findCompletedCertificateIdsWithoutTerms())
        .willReturn(List.of(id));
    given(analysisTermsRelinker.relink(id, false)).willReturn(matched(id, 3, 2));

    TermsBackfillSummary summary = service.backfill(TermsBackfillMode.MISSING_TERMS);

    // 이미 붙은 분석까지 건드리면 멀쩡한 연결이 끊길 수 있다. 기본값은 비어 있는 것만이다.
    verify(analysisResultRepository, never()).findCompletedCertificateIds();
    assertThat(summary.processed()).isEqualTo(1);
    assertThat(summary.termsMatched()).isEqualTo(1);
    assertThat(summary.coveragesLinked()).isEqualTo(2);
    assertThat(summary.coveragesTotal()).isEqualTo(3);
  }

  @Test
  void relinksCoveragesOnEveryAnalysisWithoutTouchingTerms() {
    UUID id = UUID.randomUUID();
    given(analysisResultRepository.findCompletedCertificateIds()).willReturn(List.of(id));
    given(analysisTermsRelinker.relink(id, false)).willReturn(matched(id, 3, 3));

    service.backfill(TermsBackfillMode.RELINK_COVERAGES);

    // 규칙을 새로 적재했거나 담보 매칭 단계를 늘린 뒤에 쓰는 경로다. 이미 붙은 약관은 그대로 둔다.
    verify(analysisResultRepository, never()).findCompletedCertificateIdsWithoutTerms();
    verify(analysisTermsRelinker).relink(id, false);
  }

  @Test
  void targetsEveryCompletedAnalysisWhenRematching() {
    UUID id = UUID.randomUUID();
    given(analysisResultRepository.findCompletedCertificateIds()).willReturn(List.of(id));
    given(analysisTermsRelinker.relink(id, true)).willReturn(matched(id, 1, 1));

    service.backfill(TermsBackfillMode.REMATCH);

    // 약관을 새로 적재했거나 매칭 규칙을 고친 뒤 예전 판단을 다시 내리는 경로다.
    verify(analysisResultRepository, never()).findCompletedCertificateIdsWithoutTerms();
    verify(analysisTermsRelinker).relink(id, true);
  }

  @Test
  void countsUnmatchedSeparatelyFromFailed() {
    UUID matchedId = UUID.randomUUID();
    UUID unmatchedId = UUID.randomUUID();
    given(analysisResultRepository.findCompletedCertificateIdsWithoutTerms())
        .willReturn(List.of(matchedId, unmatchedId));
    given(analysisTermsRelinker.relink(matchedId, false)).willReturn(matched(matchedId, 2, 2));
    given(analysisTermsRelinker.relink(unmatchedId, false)).willReturn(unmatched(unmatchedId));

    TermsBackfillSummary summary = service.backfill(TermsBackfillMode.MISSING_TERMS);

    // 약관을 못 찾는 것은 정상 갈래다. 그 상품 약관이 아직 없다는 뜻이지 백필이 깨진 것이 아니다.
    assertThat(summary.termsMatched()).isEqualTo(1);
    assertThat(summary.termsUnmatched()).isEqualTo(1);
    assertThat(summary.failed()).isZero();
  }

  @Test
  void keepsGoingWhenOneAnalysisFails() {
    UUID broken = UUID.randomUUID();
    UUID healthy = UUID.randomUUID();
    given(analysisResultRepository.findCompletedCertificateIdsWithoutTerms())
        .willReturn(List.of(broken, healthy));
    given(analysisTermsRelinker.relink(broken, false))
        .willThrow(new IllegalStateException("깨진 행"));
    given(analysisTermsRelinker.relink(healthy, false)).willReturn(matched(healthy, 4, 4));

    TermsBackfillSummary summary = service.backfill(TermsBackfillMode.MISSING_TERMS);

    // 한 건의 문제로 멈추면 나머지 사용자는 계속 보장 상세를 못 본다.
    assertThat(summary.failedAnalysisIds()).containsExactly(broken);
    assertThat(summary.termsMatched()).isEqualTo(1);
    assertThat(summary.coveragesLinked()).isEqualTo(4);
  }

  @Test
  void doesNothingWhenNoTargets() {
    given(analysisResultRepository.findCompletedCertificateIdsWithoutTerms()).willReturn(List.of());

    TermsBackfillSummary summary = service.backfill(TermsBackfillMode.MISSING_TERMS);

    assertThat(summary.processed()).isZero();
    verify(analysisTermsRelinker, never()).relink(any(), anyBoolean());
  }

  private AnalysisTermsRelinkResult matched(UUID id, int total, int linked) {
    return new AnalysisTermsRelinkResult(
        id, true, new CoverageTermsLinkSummary(total, linked, 0, 0, List.of()));
  }

  private AnalysisTermsRelinkResult unmatched(UUID id) {
    return new AnalysisTermsRelinkResult(id, false, CoverageTermsLinkSummary.empty());
  }
}
