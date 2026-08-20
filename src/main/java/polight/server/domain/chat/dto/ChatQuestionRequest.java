package polight.server.domain.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 프론트가 보내는 질문 본문.
 *
 * <p>필드가 하나뿐인 이유는 나머지를 서버가 정하기 때문이다. 세션은 여행당 하나를 서버가 확보하고, 검색 범위는 여행 단위로 고정하며, 대화 이력은 서버가
 * {@code chat_messages}에서 잘라 쓴다. 프론트는 여행과 질문만 알면 된다.
 *
 * @param question 사용자 질문. 2000자는 프롬프트 길이를 예측 가능하게 두기 위한 상한이다
 */
public record ChatQuestionRequest(
    @Schema(description = "사용자 질문", example = "항공편이 지연되면 보상되나요?")
        @NotBlank(message = "질문을 입력해 주세요.")
        @Size(max = 2000, message = "질문은 2000자를 넘을 수 없습니다.")
        String question) {}
