package polight.server.domain.chat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import polight.server.domain.chat.dto.ChatAnswerResponse;
import polight.server.domain.chat.dto.ChatHistoryResponse;
import polight.server.domain.chat.dto.ChatQuestionRequest;
import polight.server.domain.chat.service.ChatHistoryService;
import polight.server.domain.chat.service.ChatQueryService;
import polight.server.global.exception.ErrorResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat/unregistered/messages")
@Tag(name = "Unregistered Chat", description = "등록된 여행이 없는 사용자의 챗봇 API")
@ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED",
    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "409", description = "CHAT_TRIP_ALREADY_REGISTERED: 종료 여부와 관계없이 여행이 있으면 기존 여행 챗봇 사용",
    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public class UnregisteredChatController {

  private final ChatQueryService queryService;
  private final ChatHistoryService historyService;

  @PostMapping
  @Operation(summary = "무증권자 챗봇에 질문하기",
      description = "JWT 사용자의 여행이 하나도 없을 때 호출합니다. tripId나 사용자 ID를 전달하지 않습니다. "
          + "사용자별 여행 없는 세션을 재사용하고 질문과 답변을 저장합니다. AI에는 certificateStatus=UNREGISTERED, "
          + "tripId=null, termsId=null, coverages=[]를 전달하며 임의 약관 검색 및 보장 단정을 금지합니다. "
          + "AI 구조화 응답 형식은 미확정이며 현재는 기존 TEXT 응답 계약을 사용합니다. "
          + "기본 DISABLED 모드는 503이며 질문을 저장하지 않습니다. MOCK은 local/dev/test 전용 개발 응답입니다. "
          + "AI 모드는 tripId=null 및 응답 계약 합의 후 활성화합니다. AI 실패 시 질문은 저장되며 502입니다.")
  @ApiResponse(responseCode = "200", description = "저장된 챗봇 답변",
      content = @Content(schema = @Schema(implementation = ChatAnswerResponse.class)))
  @ApiResponse(responseCode = "400", description = "INVALID_INPUT: 질문 누락, 공백 또는 2000자 초과",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(responseCode = "502", description = "AI_CHAT_REQUEST_FAILED",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(responseCode = "503", description = "UNREGISTERED_CHAT_NOT_READY",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public ChatAnswerResponse ask(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody ChatQuestionRequest request) {
    return queryService.askUnregistered(userId, request);
  }

  @GetMapping
  @Operation(summary = "무증권자 대화 이력 조회",
      description = "JWT 사용자의 여행 없는 대화 세션만 조회합니다. 대화가 없으면 sessionId=null, messages=[]입니다. "
          + "메시지는 오래된 순서로 반환하고 답변 메타데이터는 질문 응답과 동일하게 복원합니다. "
          + "limit 기본값은 50, 범위는 1~200입니다. 여행 등록 후에도 기존 대화는 삭제하지 않지만 이 API는 409를 반환합니다.")
  @ApiResponse(responseCode = "200", description = "현재 사용자의 무증권자 대화 이력",
      content = @Content(schema = @Schema(implementation = ChatHistoryResponse.class)))
  public ChatHistoryResponse getMessages(
      @AuthenticationPrincipal UUID userId, @RequestParam(required = false) Integer limit) {
    return historyService.getUnregisteredMessages(userId, limit);
  }
}
