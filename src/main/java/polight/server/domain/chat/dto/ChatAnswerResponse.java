package polight.server.domain.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import polight.server.domain.chat.entity.ChatResponseType;

/**
 * 질문 한 번에 대한 응답.
 *
 * @param responseType 지금은 항상 {@code TEXT}다. 카드형 4종은 렌더링에 필요한 데이터 출처가 아직 없다
 * @param suggestedContacts 이 답변과 함께 띄우면 좋은 현지 연락처 종류. 사고 정황이 아니면 빈 배열이다
 * @param sources 답변의 근거가 된 약관 원문 조각. 화면에 쓰지 않아도 되지만, 답변이 이상할 때 어느 조항을 보고 답했는지 확인하는 데 쓴다
 */
public record ChatAnswerResponse(
    @Schema(description = "이 여행의 대화 세션 id") UUID sessionId,
    @Schema(description = "저장된 답변 메시지 id") UUID messageId,
    String answer,
    ChatResponseType responseType,
    @Schema(
            description =
                "함께 띄울 현지 연락처 종류. 사고 정황이 아니면 빈 배열. "
                    + "현재 알려진 값은 HOSPITAL, POLICE, EMBASSY 이며 늘어날 수 있다 "
                    + "-- 모르는 값은 그리지 않고 넘기면 된다",
            example = "[\"POLICE\"]")
        List<String> suggestedContacts,
    List<SourceResponse> sources) {

  /**
   * 답변 근거 1건.
   *
   * <p>조항 위치({@code sectionTitle}·{@code clausePath})와 페이지 범위, {@code clauseType}, 그리고 원문
   * 전체({@code text})는 서버가 {@code policy_terms_chunks}에서, 그것도 질의에 지목한 약관에 속한 청크에서만 채운다.
   * 청크를 찾지 못하면 AI가 함께 보낸 값으로 채우고, 그것도 없으면 빈다. 어느 쪽이든 인용문은 그대로 남는다.
   *
   * <p>{@code documentId}는 AI가 보낸 값을 그대로 흘린다. 약관 청크는 문서가 아니라 약관에 매달려 있어 서버가 채울 값이 없다 --
   * 비어 있을 수 있다.
   *
   * @param index 답변 안에서 이 근거가 몇 번째인지. AI가 매기는 값이라 서버가 대신 채우지 않는다
   * @param quote 답변의 근거가 된 <b>발췌</b>. 말풍선 옆에 짧게 붙이는 용도다
   * @param text 그 조항의 <b>원문 전체</b>. "약관 원문" 화면처럼 잘리지 않은 본문이 필요할 때 쓴다.
   *     {@code quote}와 달리 수천 자일 수 있다
   * @param cited 이 근거가 답변 문장에 실제로 인용됐는지. AI가 보내기 전 버전에서는 {@code null}이며,
   *     {@code null}은 "모름"이므로 걸러내지 말고 그대로 그리면 된다
   */
  public record SourceResponse(
      UUID chunkId,
      UUID documentId,
      Integer index,
      @Schema(example = "제3관 배상책임 특별약관") String sectionTitle,
      @Schema(example = "제3관 > 제12조") String clausePath,
      Integer pageStart,
      Integer pageEnd,
      @Schema(
              description =
                  "조항 성격. 현재 알려진 값은 GENERAL, COVERAGE, EXCLUSION, CONDITION, LIMIT, "
                      + "DEFINITION, PROCEDURE, REQUIRED_DOCUMENT 이며 늘어날 수 있다 "
                      + "-- 모르는 값은 그리지 않고 넘기면 된다",
              example = "COVERAGE")
          String clauseType,
      @Schema(description = "근거 발췌. 짧다", example = "항공기 지연으로 인하여…") String quote,
      @Schema(description = "조항 원문 전체. 잘려 있지 않다 -- 수천 자일 수 있다") String text,
      @Schema(description = "답변에 실제로 인용됐는지. null 이면 모름이므로 걸러내지 말 것") Boolean cited) {}
}
