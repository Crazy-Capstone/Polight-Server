package polight.server.domain.chat.dto;

import java.util.List;
import java.util.UUID;

/**
 * AI 서버의 약관 질의 응답.
 *
 * @param responseType AI 서버는 항상 {@code TEXT}를 보낸다. 카드형 4종을 보내와도 서버가 {@code TEXT}로 저장한다
 * @param suggestedContacts 이 답변과 함께 띄우면 좋은 현지 연락처 <b>종류</b>({@code HOSPITAL} / {@code POLICE} /
 *     {@code EMBASSY}). 번호가 아니다 -- 어느 종류를 띄울지만 알려주고 실제 연락처는 프론트가 가진다.
 *     <p>{@code responseType}과 따로 두는 이유는 두 가지다. "도난당했는데 보상되나요"는 약관 답변과 경찰 연락처가
 *     함께 필요한데, {@code responseType}을 {@code EMERGENCY_CONTACTS}로 바꾸면 프론트가 텍스트 대신 카드를 그려
 *     보상 설명이 사라진다. 그리고 병원과 경찰이 동시에 필요한 경우를 값 하나로는 표현할 수 없다.
 *     <p>사고 정황이 아니면 빈 배열이다. AI가 생략할 수 있으므로 {@code null}일 수 있다
 * @param sources LLM이 생성한 문장이 아니라 검색된 원문에서 잘라낸 인용이다. AI가 생략할 수 있으므로 {@code null}일 수 있다
 */
public record RagQueryResponse(
    String answer, String responseType, List<String> suggestedContacts, List<Source> sources) {

  public record Source(UUID chunkId, UUID documentId, Integer page, String quote) {}
}
