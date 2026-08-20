package polight.server.domain.chat.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import polight.server.domain.chat.dto.RagQueryRequest;
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
    } catch (RuntimeException exception) {
      throw new BaseException(ErrorCode.AI_CHAT_REQUEST_FAILED, exception);
    }
  }

  private RagQueryResponse retry(RagQueryRequest request) {
    try {
      return send(request);
    } catch (BaseException alreadyClassified) {
      throw alreadyClassified;
    } catch (RuntimeException exception) {
      throw new BaseException(ErrorCode.AI_CHAT_REQUEST_FAILED, exception);
    }
  }

  private RagQueryResponse send(RagQueryRequest request) {
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
