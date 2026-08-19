package polight.server.domain.analysis.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import polight.server.domain.analysis.dto.AnalysisCallbackRequest;
import polight.server.domain.analysis.dto.AnalysisFailureCallbackRequest;
import polight.server.domain.analysis.service.AnalysisCallbackService;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

/**
 * AI 서버가 분석 결과를 알려주는 콜백 수신부.
 *
 * <p>인증은 {@code InternalApiKeyFilter}가 {@code X-Internal-Api-Key} 헤더로 처리한다. 사용자 토큰이 오지 않는 구간이다.
 *
 * <p>본문을 {@link JsonNode}로 먼저 받는다. 타입 변환은 그 다음에 하고, <b>받은 본문 원문을 그대로 보관</b>하기 위해서다. DTO로 바로 받으면
 * DTO에 없는 필드가 사라지는데, 지금 콜백에는 저장할 컬럼이 아직 없는 값(보험사명·상품명)이 실려 오고 앞으로도 필드가 추가된다.
 *
 * <p>{@link Hidden}으로 공개 API 문서에서 제외한다. {@code /v3/api-docs}는 인증 없이 열려 있어 프론트엔드가 보는 문서인데, 서버 간 계약이
 * 거기 섞이면 혼란만 준다.
 */
@Slf4j
@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/analysis-results/{analysisResultId}")
public class InternalAnalysisCallbackController {

  private final AnalysisCallbackService analysisCallbackService;
  private final ObjectMapper objectMapper;

  @PostMapping("/complete")
  public ResponseEntity<Void> complete(
      @PathVariable UUID analysisResultId, @RequestBody JsonNode body) {
    AnalysisCallbackRequest request = convert(body, AnalysisCallbackRequest.class);
    analysisCallbackService.complete(analysisResultId, request, body.toString());

    return ResponseEntity.noContent().build();
  }

  @PostMapping("/fail")
  public ResponseEntity<Void> fail(
      @PathVariable UUID analysisResultId, @RequestBody JsonNode body) {
    AnalysisFailureCallbackRequest request = convert(body, AnalysisFailureCallbackRequest.class);
    analysisCallbackService.fail(analysisResultId, request.errorMessage());

    return ResponseEntity.noContent().build();
  }

  /** 변환 실패는 AI 쪽 본문 문제이므로 400으로 돌려준다. 어디가 어긋났는지는 로그에만 남긴다. */
  private <T> T convert(JsonNode body, Class<T> type) {
    try {
      return objectMapper.convertValue(body, type);
    } catch (IllegalArgumentException exception) {
      log.warn("콜백 본문을 해석할 수 없습니다: type={}", type.getSimpleName(), exception);
      throw new BaseException(ErrorCode.INVALID_INPUT, exception);
    }
  }
}
