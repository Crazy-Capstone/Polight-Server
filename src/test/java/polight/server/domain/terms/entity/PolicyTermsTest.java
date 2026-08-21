package polight.server.domain.terms.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import polight.server.domain.insurance.entity.DocumentKind;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.user.entity.User;

class PolicyTermsTest {

  @Test
  void 증권은_약관으로_등록할_수_없다() {
    // 증권은 그 사람의 가입 내역이라 공유 대상이 아니다. 공용 약관으로 올라가면 남의 가입금액과
    // 증권번호가 다른 사용자의 답변 근거로 쓰인다.
    PolicyDocument certificate = document(DocumentKind.CERTIFICATE, user(UUID.randomUUID()));

    assertThatThrownBy(() -> PolicyTerms.fromUserUpload(certificate, "삼성화재", "해외여행보험", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("TERMS");
  }

  @Test
  void 사용자_업로드_약관은_UNVERIFIED_이고_올린_사람이_주인이다() {
    UUID userId = UUID.randomUUID();
    PolicyDocument terms = document(DocumentKind.TERMS, user(userId));

    PolicyTerms created = PolicyTerms.fromUserUpload(terms, "삼성화재", "해외여행보험", "hash");

    assertThat(created.getVerificationStatus()).isEqualTo(TermsVerificationStatus.UNVERIFIED);
    assertThat(created.getSource()).isEqualTo(TermsSource.USER_UPLOAD);
    assertThat(created.getOwnerUser().getId()).isEqualTo(userId);
    assertThat(created.getSourceDocument()).isSameAs(terms);
  }

  @Test
  void UNVERIFIED_약관은_올린_사람만_쓸_수_있다() {
    UUID ownerId = UUID.randomUUID();
    PolicyTerms mine = PolicyTerms.fromUserUpload(document(DocumentKind.TERMS, user(ownerId)), "삼성화재", "해외여행보험", null);

    assertThat(mine.isUsableBy(ownerId)).isTrue();
    assertThat(mine.isUsableBy(UUID.randomUUID())).isFalse();
    assertThat(mine.isUsableBy(null)).isFalse();
  }

  @Test
  void VERIFIED_약관은_누구나_쓸_수_있다() {
    PolicyTerms official = PolicyTerms.official("삼성화재", "해외여행보험", null, null);

    assertThat(official.isUsableBy(UUID.randomUUID())).isTrue();
    assertThat(official.isUsableBy(null)).isTrue();
  }

  @Test
  void 승격해도_출처는_남긴다() {
    // 나중에 그 약관이 잘못된 것으로 밝혀졌을 때 어디서 들어온 것인지 남아 있어야 한다.
    UUID ownerId = UUID.randomUUID();
    PolicyTerms uploaded = PolicyTerms.fromUserUpload(document(DocumentKind.TERMS, user(ownerId)), "삼성화재", "해외여행보험", null);

    uploaded.markVerified();

    assertThat(uploaded.isVerified()).isTrue();
    assertThat(uploaded.getSource()).isEqualTo(TermsSource.USER_UPLOAD);
    assertThat(uploaded.getOwnerUser().getId()).isEqualTo(ownerId);
  }

  @Test
  void 주인_없는_UNVERIFIED_약관은_만들_수_없다() {
    // 그런 행은 isUsableBy 에서 누구의 것도 아니게 되어 아무도 볼 수 없는 죽은 데이터가 된다.
    // DB CHECK 와 같은 규칙을 만드는 자리에서도 막는다.
    assertThatThrownBy(
            () ->
                PolicyTerms.builder()
                    .insurerName("삼성화재")
                    .productName("해외여행보험")
                    .verificationStatus(TermsVerificationStatus.UNVERIFIED)
                    .source(TermsSource.USER_UPLOAD)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ownerUser");
  }

  @Test
  void 보험사명과_상품명은_비어_있을_수_없다() {
    assertThatThrownBy(() -> PolicyTerms.official(" ", "해외여행보험", null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("insurerName");
    assertThatThrownBy(() -> PolicyTerms.official("삼성화재", null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("productName");
  }

  private static User user(UUID id) {
    User user =
        User.builder().provider(User.Provider.KAKAO).providerId(id.toString()).name("테스터").build();
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  private static PolicyDocument document(DocumentKind kind, User owner) {
    return PolicyDocument.builder()
        .user(owner)
        .originalFilename("문서.pdf")
        .storedFilePath("/문서.pdf")
        .documentKind(kind)
        .build();
  }
}
