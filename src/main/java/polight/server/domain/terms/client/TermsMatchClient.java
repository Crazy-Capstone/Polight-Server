package polight.server.domain.terms.client;

import polight.server.domain.terms.dto.TermsMatchRequest;
import polight.server.domain.terms.dto.TermsMatchResponse;

/** AI 서버에 "이 증권은 어느 약관인가"를 묻는다. */
public interface TermsMatchClient {

  /**
   * @return 매칭 결과. 못 찾은 것도 정상 응답이며 {@code termsId}가 비어 온다
   * @throws polight.server.global.exception.BaseException 물어보지 못했을 때. 못 찾은 것과 다르다
   */
  TermsMatchResponse match(TermsMatchRequest request);
}
