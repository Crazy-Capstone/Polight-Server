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
          - `responseType` 은 현재 항상 `TEXT` 입니다. 아래 `suggestedContacts` 가 차 있을 때도 마찬가지입니다.
          - `suggestedContacts` 는 **이 답변과 함께 띄우면 좋은 현지 연락처 종류**입니다. 번호가 아니라 종류만 옵니다.
            - `HOSPITAL`(부상·질병·치료) / `POLICE`(도난·분실·폭행) / `EMBASSY`(여권 분실, 체포·구금, 사망·실종) 중 **0개 이상**. 단순 약관 문의면 빈 배열입니다.
            - **여러 개가 올 수 있습니다.** "여권을 도난당했어요" 는 `["POLICE", "EMBASSY"]` 입니다.
            - 답변 말풍선은 평소대로 그리고 연락처는 **곁들여** 띄우세요. 카드로 바꾸면 방금 받은 약관 답변이 화면에서 사라집니다.
            - 목록에 없는 값이 올 수 있습니다(AI 가 종류를 늘리는 경우). **모르는 값은 무시**하세요 -- 서버는 막지 않고 그대로 내려줍니다.
          - `sources` 는 답변의 근거가 된 약관 조항입니다.
            - **`quote` 와 `text` 는 다릅니다.** `quote` 는 AI 가 고른 **짧은 발췌**이고, `text` 는 그 조항의 **원문 전체**입니다.
              "약관 원문" 화면처럼 잘리지 않은 본문이 필요하면 `text` 를 쓰세요 -- 수천 자일 수 있습니다.
            - `index` 는 답변 안에서 몇 번째 근거인지, `cited` 는 답변 문장에 실제로 인용됐는지입니다. **`cited` 가 `null` 이면 "모름"이므로 걸러내지 마세요.**
            - `clauseType` 은 조항 성격입니다. 목록에 없는 값이 올 수 있으니 모르는 값은 무시하세요.
            - `sectionTitle`·`clausePath`·`pageStart`·`pageEnd`·`clauseType`·`text` 는 비어 있을 수 있습니다. 그때도 `quote` 는 남습니다.
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
            **답변받은 그 시점의 약관 본문 그대로** 돌려주므로, 약관이 개정돼도 과거 상담 기록에는 사용자가 그때 본 문장이 남습니다.
            다만 `text`·`clauseType`·`index`·`cited` 가 붙기 전에 저장된 메시지는 그 필드들이 `null` 입니다.
          - `suggestedContacts` 도 질문 API 와 같은 값이 그대로 돌아옵니다. 사용자 메시지에는 항상 빈 배열입니다.
            이 필드가 붙기 전에 저장된 메시지는 빈 배열로 나옵니다 -- 그때 받은 연락처를 되살릴 방법은 없습니다.
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
