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
