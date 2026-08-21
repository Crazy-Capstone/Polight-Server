package polight.server.domain.terms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.insurance.entity.DocumentKind;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.terms.entity.PolicyTerms;
import polight.server.domain.terms.repository.PolicyTermsRepository;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
class PolicyTermsMatchingServiceTest {

  @Mock private PolicyTermsRepository policyTermsRepository;

  private PolicyTermsMatchingService service;
  private UUID userId;
  private User user;

  @BeforeEach
  void setUp() {
    service = new PolicyTermsMatchingService(policyTermsRepository);
    userId = UUID.randomUUID();
    user = user(userId);
  }

  // -------------------------------------------------------------------------
  // EXACT
  // -------------------------------------------------------------------------

  @Test
  void 보험사와_상품명이_맞는_약관이_하나면_연결한다() {
    PolicyTerms terms = verified("삼성화재해상보험", "무배당 다이렉트 해외여행보험", null, null);
    givenUsableTerms(terms);

    TermsMatch match = service.matchAndLink(certificateAnalysis("삼성화재해상보험", "무배당 다이렉트 해외여행보험", null));

    assertThat(match.stage()).isEqualTo(TermsMatchStage.EXACT);
    assertThat(match.terms()).isSameAs(terms);
  }

  @Test
  void 표기가_흔들려도_같은_상품이면_찾아낸다() {
    // 증권 OCR 결과와 운영자 입력값의 표기가 다른 것이 기본이다. 글자 그대로 비교하면 약관이
    // 등록되어 있어도 거의 항상 못 찾는다.
    PolicyTerms terms = verified("삼성화재해상보험 주식회사", "무배당다이렉트해외여행보험", null, null);
    givenUsableTerms(terms);

    TermsMatch match = service.match(certificateAnalysis("삼성화재해상보험(주)", "무배당 다이렉트 해외여행보험", null));

    assertThat(match.stage()).isEqualTo(TermsMatchStage.EXACT);
  }

  @Test
  void 연결_결과를_분석에_기록한다() {
    PolicyTerms terms = verified("삼성화재", "해외여행보험", null, null);
    givenUsableTerms(terms);
    AnalysisResult analysis = certificateAnalysis("삼성화재", "해외여행보험", null);

    service.matchAndLink(analysis);

    assertThat(analysis.getMatchedTerms()).isSameAs(terms);
    assertThat(analysis.hasMatchedTerms()).isTrue();
  }

  // -------------------------------------------------------------------------
  // REVISION
  // -------------------------------------------------------------------------

  @Test
  void 개정판이_여럿이면_여행_시작일에_유효했던_것을_고른다() {
    PolicyTerms old = verified("삼성화재", "해외여행보험", "2024.01", LocalDate.of(2024, 1, 1));
    PolicyTerms current = verified("삼성화재", "해외여행보험", "2026.01", LocalDate.of(2026, 1, 1));
    PolicyTerms future = verified("삼성화재", "해외여행보험", "2026.07", LocalDate.of(2026, 7, 1));
    givenUsableTerms(old, current, future);

    TermsMatch match =
        service.match(certificateAnalysis("삼성화재", "해외여행보험", LocalDate.of(2026, 3, 10)));

    assertThat(match.stage()).isEqualTo(TermsMatchStage.REVISION);
    // 2026-03 가입 증권은 2026-01 개정판을 적용받는다. 아직 시행되지 않은 2026-07 이 아니다.
    assertThat(match.terms()).isSameAs(current);
  }

  @Test
  void 기준일을_모르면_최신_개정판을_고른다() {
    PolicyTerms old = verified("삼성화재", "해외여행보험", "2024.01", LocalDate.of(2024, 1, 1));
    PolicyTerms latest = verified("삼성화재", "해외여행보험", "2026.01", LocalDate.of(2026, 1, 1));
    givenUsableTerms(old, latest);

    // 여행이 연결되지 않은 문서라 기준일이 없다.
    TermsMatch match = service.match(certificateAnalysis("삼성화재", "해외여행보험", null));

    assertThat(match.stage()).isEqualTo(TermsMatchStage.REVISION);
    assertThat(match.terms()).isSameAs(latest);
    assertThat(match.reason()).contains("기준일을 알 수 없어");
  }

  @Test
  void 여행_시점에_유효했던_개정판이_없으면_연결하지_않는다() {
    // 등록된 것이 전부 여행 이후 개정판이면, 이 증권이 적용받는 조항은 등록되어 있지 않다.
    // 최신판을 대신 붙이면 실제로는 없던 조항을 근거로 답하게 된다.
    givenUsableTerms(
        verified("삼성화재", "해외여행보험", "2026.07", LocalDate.of(2026, 7, 1)),
        verified("삼성화재", "해외여행보험", "2027.01", LocalDate.of(2027, 1, 1)));

    TermsMatch match =
        service.match(certificateAnalysis("삼성화재", "해외여행보험", LocalDate.of(2026, 3, 10)));

    assertThat(match.stage()).isEqualTo(TermsMatchStage.NONE);
    assertThat(match.terms()).isNull();
  }

  @Test
  void 개정일이_없는_후보가_여럿이면_가릴_수_없어_연결하지_않는다() {
    givenUsableTerms(
        verified("삼성화재", "해외여행보험", "가", null), verified("삼성화재", "해외여행보험", "나", null));

    TermsMatch match =
        service.match(certificateAnalysis("삼성화재", "해외여행보험", LocalDate.of(2026, 3, 10)));

    assertThat(match.stage()).isEqualTo(TermsMatchStage.NONE);
    assertThat(match.reason()).contains("개정일");
  }

  // -------------------------------------------------------------------------
  // INSURER
  // -------------------------------------------------------------------------

  @Test
  void 상품명이_안_맞아도_그_보험사_약관이_하나뿐이면_연결한다() {
    PolicyTerms only = verified("삼성화재", "해외여행보험", null, null);
    givenUsableTerms(only);

    TermsMatch match = service.match(certificateAnalysis("삼성화재", "듣도보도못한상품", null));

    assertThat(match.stage()).isEqualTo(TermsMatchStage.INSURER);
    assertThat(match.terms()).isSameAs(only);
  }

  @Test
  void 상품명이_안_맞는데_그_보험사_약관이_여럿이면_연결하지_않는다() {
    // 여기서 아무거나 고르면 다른 상품의 조항을 근거로 답하게 된다. 잘못 연결된 약관은 연결이
    // 없는 것보다 나쁘다.
    givenUsableTerms(
        verified("삼성화재", "해외여행보험", null, null), verified("삼성화재", "국내여행보험", null, null));

    TermsMatch match = service.match(certificateAnalysis("삼성화재", "듣도보도못한상품", null));

    assertThat(match.stage()).isEqualTo(TermsMatchStage.NONE);
    assertThat(match.terms()).isNull();
  }

  // -------------------------------------------------------------------------
  // NONE
  // -------------------------------------------------------------------------

  @Test
  void 그_보험사_약관이_아예_없으면_연결하지_않는다() {
    givenUsableTerms(verified("현대해상", "해외여행보험", null, null));

    TermsMatch match = service.match(certificateAnalysis("삼성화재", "해외여행보험", null));

    assertThat(match.stage()).isEqualTo(TermsMatchStage.NONE);
    assertThat(match.reason()).contains("삼성화재");
  }

  @Test
  void 보험사명을_읽지_못한_증권은_상품명만으로_찾지_않는다() {
    // "해외여행보험"은 어느 보험사에나 있다. 상품명만으로 붙이면 다른 회사 약관에 연결된다.
    TermsMatch match = service.match(certificateAnalysis(null, "해외여행보험", null));

    assertThat(match.stage()).isEqualTo(TermsMatchStage.NONE);
    assertThat(match.reason()).contains("보험사명");
  }

  @Test
  void 약관_문서의_분석에는_약관을_붙이지_않는다() {
    // 그 분석의 산출물이 곧 약관 자체다. 여기서 다른 약관을 가리키게 하면 근거 연결이 뒤엉킨다.
    AnalysisResult termsAnalysis =
        analysis(DocumentKind.TERMS, "삼성화재", "해외여행보험", null);

    TermsMatch match = service.match(termsAnalysis);

    assertThat(match.stage()).isEqualTo(TermsMatchStage.NONE);
    assertThat(match.reason()).contains("증권 분석이 아니라");
  }

  @Test
  void 못_찾으면_이전_연결을_끊는다() {
    // 재분석이 다른 이름을 읽었는데 이전 연결이 남아 있으면 다른 상품의 약관을 가리키게 된다.
    givenUsableTerms(verified("현대해상", "해외여행보험", null, null));
    AnalysisResult analysis = certificateAnalysis("삼성화재", "해외여행보험", null);
    analysis.linkTerms(verified("옛보험사", "옛상품", null, null));

    service.matchAndLink(analysis);

    assertThat(analysis.getMatchedTerms()).isNull();
  }

  // -------------------------------------------------------------------------
  // 접근 범위 / 우선순위
  // -------------------------------------------------------------------------

  @Test
  void 남이_올린_UNVERIFIED_약관은_후보에서_뺀다() {
    // 쿼리가 이미 걸러 주지만, 판정의 정본은 엔티티에 있다. 쿼리가 어긋나도 여기서 막혀야 한다.
    PolicyTerms someoneElses = unverified("삼성화재", "해외여행보험", UUID.randomUUID());
    givenUsableTerms(someoneElses);

    TermsMatch match = service.match(certificateAnalysis("삼성화재", "해외여행보험", null));

    assertThat(match.stage()).isEqualTo(TermsMatchStage.NONE);
  }

  @Test
  void 내가_올린_UNVERIFIED_약관은_쓸_수_있다() {
    PolicyTerms mine = unverified("삼성화재", "해외여행보험", userId);
    givenUsableTerms(mine);

    TermsMatch match = service.match(certificateAnalysis("삼성화재", "해외여행보험", null));

    assertThat(match.stage()).isEqualTo(TermsMatchStage.EXACT);
    assertThat(match.terms()).isSameAs(mine);
  }

  @Test
  void 같은_상품에_공용_약관과_내_업로드가_함께_있으면_공용_약관을_쓴다() {
    // 둘은 개정판 관계가 아니라 같은 것의 사본이다. 그대로 두면 "후보 2건"이 되어 개정판 판정으로
    // 넘어가고, 개정일이 없어 아무것도 연결되지 않는다.
    PolicyTerms official = verified("삼성화재", "해외여행보험", null, null);
    PolicyTerms mine = unverified("삼성화재", "해외여행보험", userId);
    givenUsableTerms(mine, official);

    TermsMatch match = service.match(certificateAnalysis("삼성화재", "해외여행보험", null));

    assertThat(match.stage()).isEqualTo(TermsMatchStage.EXACT);
    assertThat(match.terms()).isSameAs(official);
  }

  // -------------------------------------------------------------------------
  // 헬퍼
  // -------------------------------------------------------------------------

  private void givenUsableTerms(PolicyTerms... terms) {
    given(policyTermsRepository.findUsableBy(userId)).willReturn(List.of(terms));
  }

  private AnalysisResult certificateAnalysis(
      String insurerName, String productName, LocalDate tripStartDate) {
    return analysis(DocumentKind.CERTIFICATE, insurerName, productName, tripStartDate);
  }

  private AnalysisResult analysis(
      DocumentKind kind, String insurerName, String productName, LocalDate tripStartDate) {
    PolicyDocument document =
        PolicyDocument.builder()
            .user(user)
            .trip(tripStartDate == null ? null : trip(tripStartDate))
            .originalFilename("증권.pdf")
            .storedFilePath("/증권.pdf")
            .documentKind(kind)
            .build();

    AnalysisResult result = AnalysisResult.builder().document(document).build();
    result.completeWith(null, "{}", null, null, null, false, insurerName, productName, null);
    return result;
  }

  private Trip trip(LocalDate startDate) {
    return Trip.builder()
        .user(user)
        .name("여행")
        .startDate(startDate)
        .endDate(startDate.plusDays(5))
        .build();
  }

  private static PolicyTerms verified(
      String insurerName, String productName, String revision, LocalDate effectiveDate) {
    PolicyTerms terms = PolicyTerms.official(insurerName, productName, revision, effectiveDate);
    ReflectionTestUtils.setField(terms, "id", UUID.randomUUID());
    return terms;
  }

  private static PolicyTerms unverified(String insurerName, String productName, UUID ownerId) {
    PolicyTerms terms =
        PolicyTerms.builder()
            .insurerName(insurerName)
            .productName(productName)
            .ownerUser(user(ownerId))
            .build();
    ReflectionTestUtils.setField(terms, "id", UUID.randomUUID());
    return terms;
  }

  private static User user(UUID id) {
    User user =
        User.builder().provider(User.Provider.KAKAO).providerId(id.toString()).name("테스터").build();
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }
}
