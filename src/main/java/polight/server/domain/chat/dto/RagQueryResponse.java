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

  /**
   * 답변 근거 1건.
   *
   * <p>필드가 두 부류로 나뉜다. {@code chunkId}·{@code index}·{@code cited}·{@code quote}는 <b>이 답변에서만
   * 뜻이 있는 값</b>이라 AI만 알고, 나머지({@code sectionTitle}·{@code pageStart}·{@code pageEnd}·{@code
   * clauseType}·{@code text})는 {@code policy_terms_chunks}에도 있는 청크 자체의 속성이다.
   *
   * <p>뒤쪽을 받아 두는 이유는 DB에서 청크를 찾지 못했을 때 쓰기 위해서다. 찾았으면 DB 값을 쓴다 -- 이유는
   * {@code ChatMessageMapper#toSource} 쪽에 적어 두었다.
   *
   * @param index 답변 안에서 이 근거가 몇 번째인지. AI가 매기는 값이라 서버가 대신 채울 수 없다
   * @param page 단일 페이지. {@code pageStart}/{@code pageEnd}가 생기기 전부터 쓰던 값이라 남겨 둔다
   * @param clauseType {@code GENERAL}·{@code COVERAGE}·{@code EXCLUSION} 등. 어휘가 늘 수 있어 문자열로 받는다
   * @param text 청크 본문 전체. {@code quote}가 발췌인 것과 달리 잘려 있지 않다
   * @param cited 이 근거가 답변 문장에 실제로 인용됐는지. AI가 보내기 전에는 {@code null}이다
   */
  public record Source(
      UUID chunkId,
      UUID documentId,
      Integer index,
      String sectionTitle,
      Integer page,
      Integer pageStart,
      Integer pageEnd,
      String clauseType,
      String text,
      String quote,
      Boolean cited) {}
}
