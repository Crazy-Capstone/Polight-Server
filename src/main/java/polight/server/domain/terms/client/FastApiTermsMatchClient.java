package polight.server.domain.terms.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import polight.server.domain.terms.dto.TermsMatchRequest;
import polight.server.domain.terms.dto.TermsMatchResponse;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;
import polight.server.global.security.InternalApiKeyFilter;

/**
 * AI 서버의 {@code /internal/terms/match} 를 부른다.
 *
 * <p>재시도하지 않는다. 이 호출은 증권 분석 콜백이 커밋된 뒤 배경에서 일어나고, 실패해도 분석 결과는 이미 저장되어 있다. 여기서 버티는 대신 실패로
 * 두면 백필({@code TermsBackfillService})이 나중에 다시 붙인다 -- 그쪽이 재시도를 더 잘하는 자리다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "terms.matching.provider", havingValue = "ai")
public class FastApiTermsMatchClient implements TermsMatchClient {

  private final RestClient restClient;
  private final String termsMatchPath;
  private final String internalApiKey;

  public FastApiTermsMatchClient(
      @Qualifier("termsMatchRestClient") RestClient restClient,
      @Value("${ai.server.terms-match-path}") String termsMatchPath,
      @Value("${internal.api-key:}") String internalApiKey) {
    this.restClient = restClient;
    this.termsMatchPath = termsMatchPath;
    this.internalApiKey = internalApiKey;
  }

  @Override
  public TermsMatchResponse match(TermsMatchRequest request) {
    try {
      TermsMatchResponse response =
          restClient
              .post()
              .uri(termsMatchPath)
              .header(InternalApiKeyFilter.HEADER_NAME, internalApiKey)
              .body(request)
              .retrieve()
              .body(TermsMatchResponse.class);

      if (response == null) {
        throw new BaseException(ErrorCode.AI_TERMS_MATCH_REQUEST_FAILED);
      }
      return response;

    } catch (BaseException alreadyClassified) {
      throw alreadyClassified;

    } catch (HttpStatusCodeException rejected) {
      // 404 는 "그런 약관이 없다"로 읽는다. 합의한 계약은 200 + termsId=null 이지만, 없는 것을
      // 404 로 돌려주는 것도 흔한 선택이라 양쪽을 같은 뜻으로 받는다. 이것을 오류로 다루면
      // 정상 갈래인 "약관 미등록"이 전부 재시도 대상이 된다.
      if (rejected.getStatusCode() == HttpStatus.NOT_FOUND) {
        log.info("AI 서버가 약관을 찾지 못했습니다(404): 보험사={}", request.insurerName());
        return new TermsMatchResponse(null, "NONE", null, null, null, null);
      }

      log.warn(
          "AI 서버가 약관 매칭을 거절했습니다: status={}, 보험사={}, 상품={}",
          rejected.getStatusCode(),
          request.insurerName(),
          request.productName());
      throw new BaseException(ErrorCode.AI_TERMS_MATCH_REQUEST_FAILED, rejected);

    } catch (RuntimeException exception) {
      throw new BaseException(ErrorCode.AI_TERMS_MATCH_REQUEST_FAILED, exception);
    }
  }
}
