package polight.server.domain.chat.client;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import polight.server.domain.chat.dto.RagQueryRequest;
import polight.server.domain.chat.dto.RagQueryRequest.Coverage;
import polight.server.domain.chat.dto.RagQueryResponse;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;
import polight.server.global.security.InternalApiKeyFilter;

/**
 * AI 서버에 약관 질의를 보낸다.
 *
 * <p>분석 요청({@code FastApiAnalysisClient})과 달리 재시도를 거의 하지 않는다. 그쪽은 202만 받고 끝나 재시도가 싸지만, 질의는 LLM 생성까지
 * 기다리므로 3회 재시도하면 사용자가 타임아웃 3배를 기다린다. 화면에는 답이 늦게 오는 것과 오지 않는 것의 차이가 크지 않다.
 *
 * <p>그래서 연결 자체가 안 된 경우에만 한 번 다시 시도한다. 이건 AI 컨테이너 재시작 중일 때 바로 실패하지 않으려는 것이고, 대기 없이 즉시 재시도한다.
 * 읽기 타임아웃과 5xx는 재시도하지 않는다 -- 이미 오래 기다린 뒤이고, 같은 질문을 다시 보내면 같은 시간을 또 쓴다.
 */
@Slf4j
@Component
public class FastApiRagQueryClient implements RagQueryClient {

  /** 로그에 남길 AI 응답 본문의 최대 길이. */
  private static final int MAX_LOGGED_BODY_LENGTH = 500;

  private final RestClient restClient;
  private final String ragPath;
  private final String internalApiKey;

  public FastApiRagQueryClient(
      @Qualifier("ragRestClient") RestClient restClient,
      @Value("${ai.server.rag-path}") String ragPath,
      @Value("${internal.api-key:}") String internalApiKey) {
    this.restClient = restClient;
    this.ragPath = ragPath;
    this.internalApiKey = internalApiKey;
  }

  @Override
  public RagQueryResponse query(RagQueryRequest request) {
    try {
      return send(request);
    } catch (BaseException alreadyClassified) {
      throw alreadyClassified;
    } catch (ResourceAccessException notConnected) {
      log.warn(
          "AI 서버 연결 실패, 즉시 1회 재시도: sessionId={}, cause={}",
          request.sessionId(),
          notConnected.getMessage());
      return retry(request);
    } catch (HttpStatusCodeException rejected) {
      logRejection(request, rejected);
      throw new BaseException(ErrorCode.AI_CHAT_REQUEST_FAILED, rejected);
    } catch (RuntimeException exception) {
      throw new BaseException(ErrorCode.AI_CHAT_REQUEST_FAILED, exception);
    }
  }

  /**
   * 무엇을 실어 보내는지 한 줄로 남긴다.
   *
   * <p>지금까지는 실패한 요청만 로그가 남아, 성공한 요청에 무엇이 실렸는지 확인할 방법이 서버 쪽에 없었다. 그래서 "그 값이 안 온다"는 말을
   * 받으면 코드를 읽어 반박하는 수밖에 없었다. 여기 남는 세 값이 그 논쟁을 끝낸다 -- 검색 범위({@code termsId}), 개인화 재료(담보 수),
   * 멀티턴({@code historyTurns}).
   *
   * <p>질문과 대화 이력 본문은 남기지 않는다. 사용자가 쓴 문장이라 서버 로그에 쌓아 둘 이유가 없고, 개수만으로 원인이 갈린다.
   *
   * <p>담보명까지 봐야 하면 {@code DEBUG}로 내린다. 이름은 증권에서 읽은 상품 정보라 평소에 남기지 않는다.
   */
  private void logOutgoing(RagQueryRequest request) {
    List<Coverage> coverages = request.coverages();
    int coverageCount = coverages == null ? 0 : coverages.size();

    log.info(
        "AI 질의 전송: sessionId={}, termsId={}, 담보 {}건, coveragesComplete={}, historyTurns={}",
        request.sessionId(),
        request.termsId(),
        coverageCount,
        request.coveragesComplete(),
        request.history() == null ? 0 : request.history().size());

    if (log.isDebugEnabled() && coverageCount > 0) {
      log.debug(
          "AI 질의 담보: sessionId={}, {}",
          request.sessionId(),
          coverages.stream().map(FastApiRagQueryClient::describe).toList());
    }
  }

  /** 담보 한 건을 로그용으로 줄인다. */
  private static String describe(Coverage coverage) {
    return "%s(가입=%s, 한도=%s)"
        .formatted(coverage.name(), coverage.subscribed(), coverage.limitAmount());
  }

  /**
   * AI가 에러 상태를 돌려준 사실을 한 줄로 남긴다.
   *
   * <p>이 로그가 없으면 원인이 502 스택의 {@code Caused by} 안에 파묻힌다. 실제로 그것을 찾느라 로그를 수십 줄 뒤진 적이 있어 따로 남긴다.
   * 상태코드가 4xx면 계약이 어긋난 것이고 5xx면 AI 내부 문제라, 그 한 글자가 어느 쪽을 봐야 하는지 가른다.
   *
   * <p>질문 본문은 남기지 않는다. 사용자가 쓴 문장이라 로그에 쌓아 둘 이유가 없고, 원인 규명에는 상태코드와 AI가 돌려준 본문이면 충분하다.
   */
  private void logRejection(RagQueryRequest request, HttpStatusCodeException rejected) {
    log.warn(
        "AI 서버가 질의를 거절했습니다: sessionId={}, status={}, historyTurns={}, body={}",
        request.sessionId(),
        rejected.getStatusCode(),
        request.history() == null ? 0 : request.history().size(),
        abbreviate(rejected.getResponseBodyAsString()));
  }

  /** 본문이 통째로 로그를 덮지 않게 자른다. 원인을 가리는 것은 대개 앞부분에 있다. */
  private String abbreviate(String body) {
    if (body == null || body.length() <= MAX_LOGGED_BODY_LENGTH) {
      return body;
    }
    return body.substring(0, MAX_LOGGED_BODY_LENGTH) + "...(생략)";
  }

  private RagQueryResponse retry(RagQueryRequest request) {
    try {
      return send(request);
    } catch (BaseException alreadyClassified) {
      throw alreadyClassified;
    } catch (HttpStatusCodeException rejected) {
      logRejection(request, rejected);
      throw new BaseException(ErrorCode.AI_CHAT_REQUEST_FAILED, rejected);
    } catch (RuntimeException exception) {
      throw new BaseException(ErrorCode.AI_CHAT_REQUEST_FAILED, exception);
    }
  }

  private RagQueryResponse send(RagQueryRequest request) {
    logOutgoing(request);

    RagQueryResponse response =
        restClient
            .post()
            .uri(ragPath)
            .header(InternalApiKeyFilter.HEADER_NAME, internalApiKey)
            .body(request)
            .retrieve()
            .body(RagQueryResponse.class);

    // 200인데 본문이 비어 있으면 아래에서 NPE가 된다. 그 예외는 원인을 가리므로 여기서 끊는다.
    if (response == null || response.answer() == null) {
      throw new BaseException(ErrorCode.AI_CHAT_REQUEST_FAILED);
    }

    return response;
  }
}
