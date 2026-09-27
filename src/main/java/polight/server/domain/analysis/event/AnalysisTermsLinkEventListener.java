package polight.server.domain.analysis.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import polight.server.domain.terms.service.AnalysisTermsRelinkResult;
import polight.server.domain.terms.service.AnalysisTermsRelinker;

/**
 * 분석 결과가 커밋된 뒤 약관과 보장 규칙을 붙인다.
 *
 * <p>콜백 트랜잭션 안에서 하던 일을 여기로 뺐다. 매칭이 로컬 DB 조회였을 때는 트랜잭션 안에서 해도 응답을 늦추지 않았지만, 이 자리는 곧 AI 서버 호출로
 * 바뀐다. 그러면 DB 커넥션을 쥔 채 HTTP를 기다리게 되고, 동시 콜백이 커넥션 풀을 넘기는 순간 관련 없는 요청까지 함께 멈춘다. 게다가 AI 가 보낸 콜백을
 * 처리하는 도중에 AI 를 되부르는 모양이라, 그쪽이 느리면 콜백이 타임아웃되고 재시도가 다시 들어온다.
 *
 * <p>{@link AnalysisRequestEventListener}와 같은 방식이다. 그쪽도 같은 이유로 커밋 뒤에 AI 를 부른다.
 *
 * <p><b>대가가 있다.</b> 커밋과 이 리스너 사이에 "분석은 완료인데 약관만 비어 있는" 순간이 생긴다. 그 사이에 조회하면 약관을 찾지 못한 것과 구분되지
 * 않는다. 지금은 그 상태에 반응하는 화면이 없어 드러나지 않지만, 약관을 못 찾았을 때 사용자에게 업로드를 요청하는 흐름을 붙일 때는 반드시 구분 수단을
 * 먼저 만들어야 한다 -- 잠시 뒤면 붙을 약관인데도 업로드를 요구하게 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisTermsLinkEventListener {

  private final AnalysisTermsRelinker analysisTermsRelinker;

  /**
   * 약관을 찾아 붙이고, 그 약관의 보장 규칙을 담보에 붙인다.
   *
   * <p>연결에 실패해도 분석 결과는 이미 커밋되어 있다. 담보와 요약은 그대로 남고 약관만 비는데, 그 상태는 백필({@code TermsBackfillService})로
   * 나중에 메울 수 있다. 여기서 예외를 다시 던져 봐야 커밋된 것을 되돌리지도 못하면서 AI 쪽에 콜백 실패로 보여 재시도만 부른다.
   *
   * @param event 커밋된 분석 결과
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void linkTerms(AnalysisCompletedEvent event) {
    try {
      // rematch=true: 방금 보험사/상품명이 새로 채워졌으므로 이전 연결이 있어도 다시 판단한다.
      AnalysisTermsRelinkResult result = analysisTermsRelinker.relink(event.analysisResultId(), true);

      log.info(
          "약관 연결 완료: analysisResultId={}, 약관매칭={}, 규칙 연결 {}/{}건",
          event.analysisResultId(),
          result.termsMatched(),
          result.coverages().linked(),
          result.coverages().total());
    } catch (RuntimeException exception) {
      log.error(
          "약관 연결 실패(분석 결과는 저장되어 있습니다): analysisResultId={}",
          event.analysisResultId(),
          exception);
    }
  }
}
