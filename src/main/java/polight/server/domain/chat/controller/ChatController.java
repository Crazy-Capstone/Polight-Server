package polight.server.domain.chat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trips/{tripId}/chat")
@Tag(name = "Chat", description = "여행 약관 기반 챗봇 API")
public class ChatController {

  private final ChatQueryService chatQueryService;
  private final ChatHistoryService chatHistoryService;

  @PostMapping("/messages")
  @Operation(
      summary = "챗봇에 질문하기",
      description =
          """
          여행에 올린 약관을 근거로 답변합니다. 질문과 답변은 서버가 저장하므로 프론트가 대화 이력을 관리할 필요가 없습니다.

          - **대화 세션은 여행당 하나**입니다. 첫 질문에 자동으로 만들어지고 이후 재사용되므로 세션을 만들거나 고르는 호출이 없습니다.
          - **검색 범위는 여행 전체**입니다. 그 여행에 올린 약관이 모두 대상입니다.
          - `responseType` 은 현재 항상 `TEXT` 입니다.
          - `sources` 는 답변의 근거가 된 약관 원문입니다. 화면에 쓰지 않아도 되지만, 답변이 이상할 때 어느 조항을 보고 답했는지 확인할 수 있습니다.
          - 이 API는 AI 서버의 답변 생성을 기다립니다. 응답까지 수 초가 걸리므로 클라이언트 타임아웃을 넉넉히 두세요.

          AI 서버가 응답하지 못하면 `502` 입니다. 이때도 **보낸 질문은 저장되어 있으므로** 화면에서 지우지 마세요.
          """)
  public ChatAnswerResponse ask(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID tripId,
      @Valid @RequestBody ChatQuestionRequest request) {
    return chatQueryService.ask(userId, tripId, request);
  }

  @GetMapping("/messages")
  @Operation(
      summary = "대화 이력 조회",
      description =
          """
          이 여행의 대화를 **오래된 것부터** 돌려줍니다. 화면에 위에서 아래로 그대로 그리면 됩니다.

          - 대화가 아직 없으면 `sessionId` 는 `null`, `messages` 는 빈 배열입니다. **오류가 아닙니다.**
          - `sender` 가 `USER` 면 사용자 말풍선, `ASSISTANT` 면 챗봇 말풍선입니다.
          - `sources` 는 챗봇 메시지의 답변 근거입니다. 사용자 메시지에는 항상 빈 배열입니다.
          - 항목의 모양은 질문 API(`POST .../messages`) 응답과 같으므로 두 경로를 다르게 다룰 필요가 없습니다.

          `limit` 은 최근 몇 개를 받을지이며 기본 50, 최대 200입니다. 범위를 벗어난 값은 서버가 맞춥니다.
          더 오래된 대화를 이어서 받는 방법(커서)은 아직 없습니다.
          """)
  public ChatHistoryResponse getMessages(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID tripId,
      @RequestParam(required = false) Integer limit) {
    return chatHistoryService.getMessages(userId, tripId, limit);
  }
}
