package polight.server.domain.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import polight.server.domain.chat.entity.ChatResponseType;

/**
 * 질문 한 번에 대한 응답.
 *
 * @param responseType 지금은 항상 {@code TEXT}다. 카드형 4종은 렌더링에 필요한 데이터 출처가 아직 없다
 * @param sources 답변의 근거가 된 약관 원문 조각. 화면에 쓰지 않아도 되지만, 답변이 이상할 때 어느 조항을 보고 답했는지 확인하는 데 쓴다
 */
public record ChatAnswerResponse(
    @Schema(description = "이 여행의 대화 세션 id") UUID sessionId,
    @Schema(description = "저장된 답변 메시지 id") UUID messageId,
    String answer,
    ChatResponseType responseType,
    List<SourceResponse> sources) {

  /**
   * 답변 근거 1건.
   *
   * <p>AI 서버는 {@code chunkId}·{@code documentId}·{@code page}·{@code quote}만 돌려준다. 조항 위치({@code
   * sectionTitle}·{@code clausePath})와 페이지 범위는 서버가 {@code policy_terms_chunks}에서, 그것도 질의에 지목한
   * 약관에 속한 청크에서만 채운다. 청크를 찾지 못하면 그 필드만 비고 인용문은 그대로 남는다.
   *
   * <p>{@code documentId}는 AI가 보낸 값을 그대로 흘린다. 약관 청크는 문서가 아니라 약관에 매달려 있어 서버가 채울 값이 없다 --
   * 비어 있을 수 있다.
   */
  public record SourceResponse(
      UUID chunkId,
      UUID documentId,
      @Schema(example = "제3관 배상책임 특별약관") String sectionTitle,
      @Schema(example = "제3관 > 제12조") String clausePath,
      Integer pageStart,
      Integer pageEnd,
      String quote) {}
}
