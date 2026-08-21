package polight.server.domain.terms.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import polight.server.domain.analysis.repository.AnalysisResultRepository;

/**
 * 약관 저장소가 생기기 전에 끝난 분석들에 약관을 뒤늦게 붙인다.
 *
 * <p>지금 분석되는 증권은 완료 콜백 안에서 약관이 붙는다. 그러나 그 코드가 생기기 전에 처리된 분석에는 연결이 비어 있고, 스스로 채워질 계기가 없다 --
 * 사용자가 증권을 다시 올리지 않는 한 그 행은 다시 쓰이지 않는다. 그래서 옛 사용자는 보장 상세에서 면책·청구서류를 영영 보지 못한다. 이 서비스가 그
 * 한 번의 계기다.
 *
 * <p><b>한 건씩 각자의 트랜잭션에서 처리한다.</b> 전부를 한 트랜잭션에 묶으면 중간 한 건의 오류가 앞서 붙인 것을 전부 되돌리고, 커넥션 하나를
 * 그 시간 내내 쥔다. 실패한 분석은 건너뛰고 id를 요약에 남긴다.
 *
 * <p>여러 번 돌려도 안전하다. 같은 입력에는 같은 판단이 나오고, 무엇을 대상으로 삼을지는 {@link TermsBackfillMode}가 정한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TermsBackfillService {

  private final AnalysisResultRepository analysisResultRepository;
  private final AnalysisTermsRelinker analysisTermsRelinker;

  /**
   * 백필을 돌린다.
   *
   * @param mode 무엇을 대상으로 삼고 어디까지 다시 판단할지. {@link TermsBackfillMode} 참고
   */
  public TermsBackfillSummary backfill(TermsBackfillMode mode) {
    List<UUID> targets =
        mode.includesLinked()
            ? analysisResultRepository.findCompletedCertificateIds()
            : analysisResultRepository.findCompletedCertificateIdsWithoutTerms();

    if (targets.isEmpty()) {
      log.info("백필 대상이 없습니다: mode={}", mode);
      return TermsBackfillSummary.empty();
    }

    log.info("약관 백필 시작: 대상 {}건, mode={}", targets.size(), mode);

    int termsMatched = 0;
    int coveragesLinked = 0;
    int coveragesTotal = 0;
    List<UUID> failed = new ArrayList<>();

    for (UUID analysisResultId : targets) {
      try {
        AnalysisTermsRelinkResult result =
            analysisTermsRelinker.relink(analysisResultId, mode.rematchesTerms());
        if (result.termsMatched()) {
          termsMatched++;
        }
        coveragesLinked += result.coverages().linked();
        coveragesTotal += result.coverages().total();
      } catch (RuntimeException exception) {
        // 한 건의 문제로 백필 전체를 멈추지 않는다. 남은 건들은 여전히 붙을 수 있다.
        log.warn("백필 중 한 건을 건너뜁니다: analysisResultId={}", analysisResultId, exception);
        failed.add(analysisResultId);
      }
    }

    TermsBackfillSummary summary =
        new TermsBackfillSummary(
            targets.size(),
            termsMatched,
            targets.size() - termsMatched - failed.size(),
            coveragesLinked,
            coveragesTotal,
            List.copyOf(failed));

    log.info(
        "약관 백필 완료: 분석 {}건 중 약관 연결 {}건(미연결 {}, 실패 {}), 담보 규칙 {}/{}건",
        summary.processed(),
        summary.termsMatched(),
        summary.termsUnmatched(),
        summary.failed(),
        summary.coveragesLinked(),
        summary.coveragesTotal());

    return summary;
  }
}
