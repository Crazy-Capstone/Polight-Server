package polight.server.domain.terms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

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
import polight.server.domain.terms.entity.PolicyTerms;
import polight.server.domain.user.entity.User;

/**
 * 고르는 일이 아니라 <b>고른 결과를 반영하는 일</b>을 검증한다. 어느 약관을 고르는지는 {@link LocalTermsMatcherTest} 소관이다.
 */
@ExtendWith(MockitoExtension.class)
class PolicyTermsMatchingServiceTest {

  @Mock private TermsMatcher termsMatcher;

  @Test
  void 찾으면_분석에_연결한다() {
    PolicyTerms terms = terms();
    given(termsMatcher.match(any()))
        .willReturn(TermsMatch.found(TermsMatchStage.EXACT, terms, "일치", null));
    AnalysisResult analysis = analysis();

    service().matchAndLink(analysis);

    assertThat(analysis.getMatchedTerms()).isSameAs(terms);
  }

  @Test
  void 단계와_사용자_안내를_함께_남긴다() {
    // 셋은 한 번의 판단에서 나온 값이다. 따로 넣게 두면 약관만 바뀌고 안내는 이전 것이 남는다.
    given(termsMatcher.match(any()))
        .willReturn(
            TermsMatch.found(TermsMatchStage.INSURER, terms(), "상품명 불일치", "정확히 찾지 못했어요."));
    AnalysisResult analysis = analysis();

    service().matchAndLink(analysis);

    assertThat(analysis.getTermsMatchStage()).isEqualTo("INSURER");
    assertThat(analysis.getTermsMatchNotice()).isEqualTo("정확히 찾지 못했어요.");
  }

  @Test
  void 못_찾으면_이전_연결을_끊는다() {
    // 재분석이 다른 이름을 읽었는데 이전 연결이 남아 있으면 다른 상품의 약관을 가리키게 된다.
    given(termsMatcher.match(any())).willReturn(TermsMatch.none("등록된 약관이 없습니다."));
    AnalysisResult analysis = analysis();
    analysis.linkTerms(terms(), "EXACT", null);

    service().matchAndLink(analysis);

    assertThat(analysis.getMatchedTerms()).isNull();
    // 약관이 없다는 사실도 사용자에게 알려야 한다. 그래야 증권에 적힌 내용만 보고 있다는 것을 안다.
    assertThat(analysis.getTermsMatchNotice()).isNotBlank();
  }

  @Test
  void 판단에_실패하면_연결을_건드리지_않고_예외를_흘린다() {
    // 여기서 잡아 NONE 으로 다루면 AI 가 잠시 죽은 사이 재분석된 증권들이 멀쩡한 연결을 잃는다.
    // 흘려보내면 이 트랜잭션이 롤백되어 붙이지도 지우지도 않은 상태로 남는다.
    PolicyTerms previous = terms();
    given(termsMatcher.match(any())).willThrow(new IllegalStateException("AI 서버 응답 없음"));
    AnalysisResult analysis = analysis();
    analysis.linkTerms(previous, "EXACT", null);

    assertThatThrownBy(() -> service().matchAndLink(analysis))
        .isInstanceOf(IllegalStateException.class);
    assertThat(analysis.getMatchedTerms()).isSameAs(previous);
  }

  private PolicyTermsMatchingService service() {
    return new PolicyTermsMatchingService(termsMatcher);
  }

  private AnalysisResult analysis() {
    User user = User.builder().provider(User.Provider.KAKAO).providerId("1").name("테스터").build();
    PolicyDocument document =
        PolicyDocument.builder()
            .user(user)
            .originalFilename("증권.pdf")
            .storedFilePath("/증권.pdf")
            .documentKind(DocumentKind.CERTIFICATE)
            .build();

    AnalysisResult result = AnalysisResult.builder().document(document).build();
    result.completeWith(null, "{}", null, null, null, false, "삼성화재", "해외여행보험", null, null, null);
    return result;
  }

  private static PolicyTerms terms() {
    PolicyTerms terms =
        PolicyTerms.official("삼성화재", "해외여행보험", null, LocalDate.of(2026, 1, 1));
    ReflectionTestUtils.setField(terms, "id", UUID.randomUUID());
    return terms;
  }
}
