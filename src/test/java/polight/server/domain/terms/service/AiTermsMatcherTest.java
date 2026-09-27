package polight.server.domain.terms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willThrow;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.insurance.entity.DocumentKind;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.terms.client.TermsMatchClient;
import polight.server.domain.terms.dto.TermsMatchResponse;
import polight.server.domain.terms.entity.PolicyTerms;
import polight.server.domain.terms.repository.PolicyTermsRepository;
import polight.server.domain.user.entity.User;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class AiTermsMatcherTest {

  @Mock private TermsMatchClient termsMatchClient;
  @Mock private PolicyTermsRepository policyTermsRepository;

  @Test
  void 찾으면_그_약관으로_연결한다() {
    PolicyTerms terms = terms();
    given(termsMatchClient.match(any()))
        .willReturn(response(terms.getId(), "EXACT"));
    given(policyTermsRepository.getReferenceById(terms.getId())).willReturn(terms);

    TermsMatch match = matcher().match(certificate());

    assertThat(match.isMatched()).isTrue();
    assertThat(match.terms()).isSameAs(terms);
    assertThat(match.stage()).isEqualTo(TermsMatchStage.EXACT);
  }

  @Test
  void termsId가_비어_오면_찾지_못한_것으로_둔다() {
    // 합의한 계약이다. 못 찾는 것은 오류가 아니라 정상 갈래라 200 + null 로 온다.
    given(termsMatchClient.match(any())).willReturn(response(null, "NONE"));

    TermsMatch match = matcher().match(certificate());

    assertThat(match.stage()).isEqualTo(TermsMatchStage.NONE);
    assertThat(match.isMatched()).isFalse();
  }

  @Test
  void 물어보지_못하면_예외를_그대로_흘린다() {
    willThrow(new BaseException(ErrorCode.AI_TERMS_MATCH_REQUEST_FAILED))
        .given(termsMatchClient)
        .match(any());

    // NONE 으로 바꿔 돌려주면 호출부가 기존 연결을 지운다. 흘려보내야 연결 트랜잭션이
    // 롤백되어 붙이지도 지우지도 않은 상태로 남고, 백필이 나중에 다시 붙인다.
    assertThatThrownBy(() -> matcher().match(certificate())).isInstanceOf(BaseException.class);
  }

  @Test
  void 보험사명을_읽지_못한_증권은_묻지_않는다() {
    // 상품명만으로 찾으면 다른 보험사의 같은 이름 상품에 붙는다("해외여행보험"은 어디에나 있다).
    assertThat(matcher().match(certificate(null)).stage()).isEqualTo(TermsMatchStage.NONE);
  }

  @Test
  void 약관_문서_분석에는_약관을_붙이지_않는다() {
    assertThat(matcher().match(analysis(DocumentKind.TERMS, "삼성화재")).stage())
        .isEqualTo(TermsMatchStage.NONE);
  }

  private AiTermsMatcher matcher() {
    return new AiTermsMatcher(termsMatchClient, policyTermsRepository);
  }

  private TermsMatchResponse response(UUID termsId, String level) {
    return new TermsMatchResponse(termsId, level, null, "삼성화재", "해외여행보험", "2026-06-06");
  }

  private AnalysisResult certificate() {
    return certificate("삼성화재해상보험주식회사");
  }

  private AnalysisResult certificate(String insurerName) {
    return analysis(DocumentKind.CERTIFICATE, insurerName);
  }

  private AnalysisResult analysis(DocumentKind kind, String insurerName) {
    User user =
        User.builder().provider(User.Provider.KAKAO).providerId("1").name("테스터").build();
    PolicyDocument document =
        PolicyDocument.builder()
            .user(user)
            .originalFilename("증권.pdf")
            .storedFilePath("/증권.pdf")
            .documentKind(kind)
            .build();

    AnalysisResult result = AnalysisResult.builder().document(document).build();
    result.completeWith(
        null, "{}", null, null, null, false, insurerName, "해외여행보험", null, null, null);
    return result;
  }

  private static PolicyTerms terms() {
    PolicyTerms terms =
        PolicyTerms.official("삼성화재", "해외여행보험", null, LocalDate.of(2026, 6, 6));
    ReflectionTestUtils.setField(terms, "id", UUID.randomUUID());
    return terms;
  }
}
