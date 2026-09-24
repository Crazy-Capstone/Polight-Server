package polight.server.domain.chat.dto;

import java.util.List;
import polight.server.domain.chat.dto.ChatAnswerResponse.SourceResponse;

/**
 * {@code chat_messages.metadata_json}에 직렬화해 넣는 값.
 *
 * <p>{@code chat_messages}에는 근거를 담을 별도 컬럼이 없어 이 TEXT 컬럼이 유일한 자리다.
 *
 * <p>{@code suggestedContacts}를 여기 함께 넣는다. 이력 조회 응답이 질문 응답과 <b>같은 모양</b>이어야 프론트가 두
 * 경로를 다르게 다루지 않는데, 저장하지 않으면 앱을 껐다 켠 뒤 그 대화만 연락처가 사라진다.
 *
 * @param suggestedContacts 함께 띄울 현지 연락처 종류. 예전에 저장된 메시지에는 이 키가 없어 {@code null}일 수 있다
 * @param latencyMs AI 서버 왕복 시간. 답변이 느릴 때 검색·생성 어느 쪽 문제인지 나누려면 저장해 둔 값이 있어야 한다
 */
public record ChatMessageMetadata(
    List<SourceResponse> sources, List<String> suggestedContacts, long latencyMs) {}
