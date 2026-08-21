package polight.server.domain.chat.dto;

import java.util.List;
import polight.server.domain.chat.dto.ChatAnswerResponse.SourceResponse;

/**
 * {@code chat_messages.metadata_json}에 직렬화해 넣는 값.
 *
 * <p>{@code chat_messages}에는 근거를 담을 별도 컬럼이 없어 이 TEXT 컬럼이 유일한 자리다.
 *
 * @param latencyMs AI 서버 왕복 시간. 답변이 느릴 때 검색·생성 어느 쪽 문제인지 나누려면 저장해 둔 값이 있어야 한다
 */
public record ChatMessageMetadata(List<SourceResponse> sources, long latencyMs) {}
