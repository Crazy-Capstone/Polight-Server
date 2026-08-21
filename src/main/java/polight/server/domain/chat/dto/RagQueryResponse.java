package polight.server.domain.chat.dto;

import java.util.List;
import java.util.UUID;

/**
 * AI 서버의 약관 질의 응답.
 *
 * @param responseType AI 서버는 항상 {@code TEXT}를 보낸다. 카드형 4종을 보내와도 서버가 {@code TEXT}로 저장한다
 * @param sources LLM이 생성한 문장이 아니라 검색된 원문에서 잘라낸 인용이다. AI가 생략할 수 있으므로 {@code null}일 수 있다
 */
public record RagQueryResponse(String answer, String responseType, List<Source> sources) {

  public record Source(UUID chunkId, UUID documentId, Integer page, String quote) {}
}
