package polight.server.domain.terms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.insurance.entity.DocumentKind;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.terms.client.TermsMatchClient;
import polight.server.domain.terms.dto.TermsMatchRequest;
import polight.server.domain.terms.dto.TermsMatchResponse;
import polight.server.domain.terms.entity.PolicyTerms;
import polight.server.domain.terms.repository.PolicyTermsRepository;

/**
 * AI 서버에 약관 판단을 맡긴다.
 *
 * <p>{@link LocalTermsMatcher}가 이름 표기만 다듬어 비교하는 데 비해, AI 쪽은 표기로는 닿지 않는 것까지 안다 -- 약관코드, 보험사
 * 별칭, 인수사 이력(한화손해보험이 인수하기 전 캐롯 약관 같은 것). 실제 증권을 돌려보며 모은 값이라 코드로 재현할 수 없다.
 *
 * <p>돌려받은 값은 검사하지 않고 그대로 쓴다. "어느 약관인가"의 판단을 통째로 넘긴 것이라, 여기서 다시 따지면 판정이 두 곳에 생겨
 * 어느 쪽이 정본인지 알 수 없게 된다. 잘못된 {@code termsId}는 FK 가, 모르는 {@code level}은 {@code valueOf}가 걸러내고,
 * 그 실패는 연결 트랜잭션에만 머문다 -- 분석 결과는 이미 커밋되어 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "terms.matching.provider", havingValue = "ai")
public class AiTermsMatcher implements TermsMatcher {

  private final TermsMatchClient termsMatchClient;
  private final PolicyTermsRepository policyTermsRepository;

  @Override
  @Transactional(readOnly = true)
  public TermsMatch match(AnalysisResult analysisResult) {
    PolicyDocument document = analysisResult.getDocument();

    // 약관 문서를 분석한 결과에는 약관을 붙이지 않는다. 그 분석의 산출물이 곧 약관 자체이고,
    // 여기서 자기 자신이나 남의 약관을 가리키게 하면 근거 연결이 뒤엉킨다.
    if (document == null || document.getDocumentKind() != DocumentKind.CERTIFICATE) {
      return TermsMatch.none("증권 분석이 아니라 약관을 찾지 않습니다.");
    }

    // 보험사명이 없으면 물어볼 것이 없다. 상품명만으로는 다른 보험사의 같은 이름 상품에 붙는다.
    if (analysisResult.getInsurerName() == null || analysisResult.getInsurerName().isBlank()) {
      return TermsMatch.none("증권에서 보험사명을 읽지 못했습니다.");
    }

    // 물어보지 못하면 예외가 그대로 나간다. 잡아서 "약관 없음"으로 바꾸면 AI 가 잠시 죽은 사이
    // 재분석된 증권들이 멀쩡한 연결을 잃는다. 흘려보내면 연결 트랜잭션이 롤백되어 기존 연결이
    // 그대로 남고, 백필이 나중에 다시 시도한다.
    TermsMatchResponse response =
        termsMatchClient.match(
            new TermsMatchRequest(
                analysisResult.getInsurerName(),
                analysisResult.getProductName(),
                analysisResult.getInsuranceStartDate()));

    if (response.termsId() == null) {
      return TermsMatch.none(
          "AI 서버가 맞는 약관을 찾지 못했습니다" + (response.notice() == null ? "." : ": " + response.notice()));
    }

    // 존재를 확인하지 않고 참조만 얻는다. 어느 약관인가는 AI 가 판단하기로 한 것이라 여기서
    // 다시 따지지 않는다. 없는 id 라면 커밋 시점에 FK 가 걸러내고, 그 실패는 연결 트랜잭션에만
    // 머문다(분석 결과는 이미 커밋되어 있다).
    PolicyTerms terms = policyTermsRepository.getReferenceById(response.termsId());
    return TermsMatch.found(TermsMatchStage.valueOf(response.level()), terms, describe(response));
  }

  private String describe(TermsMatchResponse response) {
    StringBuilder reason = new StringBuilder("AI 서버 매칭(level=").append(response.level()).append(")");
    if (response.insurerName() != null) {
      reason.append(" ").append(response.insurerName());
    }
    if (response.productName() != null) {
      reason.append(" ").append(response.productName());
    }
    if (response.revision() != null) {
      reason.append(" 개정 ").append(response.revision());
    }
    return reason.toString();
  }
}
