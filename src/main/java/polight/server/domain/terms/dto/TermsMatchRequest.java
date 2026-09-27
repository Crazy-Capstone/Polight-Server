package polight.server.domain.terms.dto;

import java.time.LocalDate;

/**
 * AI 서버에 보내는 약관 매칭 질의.
 *
 * <p>세 값 모두 증권 분석 콜백으로 이미 받아 {@code analysis_results}에 들어 있다. 따로 만들어야 하는 것이 없다.
 *
 * @param insuranceStartDate 개정판이 여럿일 때 어느 판을 적용할지 가르는 기준일. 증권에서 읽지 못했으면 {@code null}이고, 그때는
 *     AI 가 최신 판을 고른다
 */
public record TermsMatchRequest(
    String insurerName, String productName, LocalDate insuranceStartDate) {}
