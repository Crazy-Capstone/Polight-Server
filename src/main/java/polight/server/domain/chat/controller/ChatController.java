package polight.server.domain.chat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import polight.server.domain.chat.dto.ChatAnswerResponse;
import polight.server.domain.chat.dto.ChatQuestionRequest;
import polight.server.domain.chat.service.ChatQueryService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trips/{tripId}/chat")
@Tag(name = "Chat", description = "여행 약관 기반 챗봇 API")
public class ChatController {

  private final ChatQueryService chatQueryService;

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
}
