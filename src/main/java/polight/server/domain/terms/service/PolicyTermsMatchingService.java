package polight.server.domain.terms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.analysis.entity.AnalysisResult;

/**
 * 증권 분석 결과를 약관에 연결한다.
 *
 * <p>AI 서버는 증권에서 읽은 것만 콜백으로 보낸다 -- 요약, 담보 목록, 보험사명, 상품명. 그것으로 "어느 약관인가"를 정하는 것이 이 클래스의 일이다.
 * 이 연결이 서야 담보의 근거 조항을 인용하고 챗봇이 약관 본문을 검색할 수 있다. 연결이 없으면 사용자는 증권에 적힌 금액만 보게 된다.
 *
 * <p>고르는 일 자체는 {@link TermsMatcher}에 맡긴다. 여기 남은 것은 그 결과를 엔티티에 반영하고 흔적을 남기는 것뿐이다 -- 어느 구현을
 * 쓰든 연결 방식과 로그는 같아야 하고, 구현을 바꿀 때 그 부분까지 다시 짜게 두면 두 벌이 서서히 어긋난다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyTermsMatchingService {

  private final TermsMatcher termsMatcher;

  /**
   * 약관을 찾아 분석에 연결한다.
   *
   * <p>찾지 못하면 연결을 {@code null}로 비운다. 재분석으로 보험사/상품명이 달라졌는데 이전 연결이 남아 있으면, 그 약관은 더 이상 이 증권의 것이
   * 아니다.
   *
   * <p><b>판단 자체에 실패하면 예외가 그대로 나간다.</b> AI 서버에 물어보지 못한 것은 "약관이 없다"가 아니라 "모른다"이고, 그때
   * 연결을 지우면 AI 가 잠시 죽은 사이에 재분석된 증권들이 멀쩡한 연결을 잃는다. 여기서 잡지 않으면 이 트랜잭션이 통째로 롤백되어
   * 저절로 그렇게 된다 -- 붙이지도, 지우지도 않은 상태로 남는다. 호출부({@code AnalysisTermsLinkEventListener})가 로그를
   * 남기고, 백필이 나중에 다시 시도한다.
   */
  @Transactional
  public TermsMatch matchAndLink(AnalysisResult analysisResult) {
    TermsMatch match = termsMatcher.match(analysisResult);
    analysisResult.linkTerms(match.terms());

    if (match.isMatched()) {
      log.info(
          "약관 연결: analysisResultId={}, termsId={}, 단계={}, 근거={}",
          analysisResult.getId(),
          match.terms().getId(),
          match.stage(),
          match.reason());
    } else {
      // 실패가 아니라 정상 갈래이므로 info 로 남긴다. 다만 어떤 이름으로 못 찾았는지는 남겨야
      // 어느 상품 약관을 등록해야 하는지 알 수 있다.
      log.info(
          "약관을 연결하지 않음: analysisResultId={}, 보험사={}, 상품={}, 근거={}",
          analysisResult.getId(),
          analysisResult.getInsurerName(),
          analysisResult.getProductName(),
          match.reason());
    }
    return match;
  }
}
