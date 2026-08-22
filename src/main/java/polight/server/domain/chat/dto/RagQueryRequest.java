package polight.server.domain.chat.dto;

import java.util.List;
import java.util.UUID;

/**
 * Spring 서버가 AI 서버에 약관 질의를 보낼 때 쓰는 내부 계약.
 *
 * <p>AI 서버의 {@code RagQueryRequest}(app/schemas/rag.py)와 필드명이 1:1로 맞아야 한다. 그쪽은 camelCase 별칭을 쓰므로 이
 * record의 필드명이 그대로 매핑된다.
 *
 * <p>AI 서버는 무상태다. 대화 이력을 조회하지 않고 이 요청에 실린 것만 본다. {@code chat_messages} 조회 권한을 AI 계정에 주지 않기 위한
 * 결정이고, 그래서 이력을 자르는 책임이 이쪽에 있다.
 *
 * @param termsId 검색할 약관. 증권 분석이 매칭해 둔 {@code analysis_results.matched_terms_id}다. 이 값이 없으면 AI를 부르지
 *     않는다 -- 어느 약관을 뒤질지 모르는 채로 검색하면 가입하지 않은 상품의 조항으로 답이 나간다
 * @param documentId 검색 범위. {@code null}이면 AI가 {@code tripId}로 여행 전체 약관을 검색한다. 챗봇 화면에 문서를 고르는 UI가
 *     없으므로 지금은 항상 {@code null}이다
 * @param policyId 항상 {@code null}이다. {@code policies} 행을 만드는 경로가 서버에 없다. AI 서버도 이 값으로 필터하지 않는다
 * @param coverages 증권에서 읽은 가입 담보. 약관에는 그 상품이 팔 수 있는 모든 특약이 실려 있어, 이 목록이 없으면 가입하지 않은 담보를 물어도 조항이
 *     검색되어 "보상됩니다"라는 틀린 답이 나간다
 * @param coveragesComplete {@code coverages}가 증권 보장내용 표 전체인지. {@code false}면 AI가 "목록에 없는 담보"를 미가입으로
 *     단정하지 않는다. AI가 원본 표의 행 수를 알 수 없어 콜백으로 오지 않으므로 당분간 항상 {@code false}다
 * @param clausePaths 검색을 좁힐 특약명. AI 쪽 실측에서 검색 품질 개선 효과가 확인되지 않아(Recall@8 동일) 보내지 않는다. 오답 방지는 {@code
 *     coverages}가 담당한다
 */
public record RagQueryRequest(
    UUID userId,
    UUID tripId,
    UUID termsId,
    UUID documentId,
    UUID policyId,
    UUID sessionId,
    String question,
    List<HistoryTurn> history,
    List<Coverage> coverages,
    boolean coveragesComplete,
    List<String> clausePaths) {

  /** @param sender {@code USER} / {@code ASSISTANT} / {@code SYSTEM}. DB CHECK 제약값이라 이 셋만 허용된다 */
  public record HistoryTurn(String sender, String content) {}

  /**
   * 가입 담보 1건.
   *
   * <p>피보험자 이름·생년월일·증권번호는 보내지 않는다. LLM에 넘길 이유가 없고 AI 쪽 스키마에도 필드가 없다.
   *
   * @param conditions 증권에 인쇄된 조건 문구 그대로. "자기부담금 10,000", "물품당 최대 20만원 한도" 같은 것이 여기 들어 있어,
   *     이것이 없으면 자기부담금을 묻는 질문에 답할 수 없다. 약관에는 "가입금액을 한도로"라고만 적혀 있어 증권에서만 얻을 수 있는 값이다
   */
  public record Coverage(
      String name,
      boolean subscribed,
      Long limitAmount,
      String limitCurrency,
      String conditions) {}
}
