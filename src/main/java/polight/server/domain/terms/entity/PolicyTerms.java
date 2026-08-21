package polight.server.domain.terms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import polight.server.domain.common.entity.BaseTimeEntity;
import polight.server.domain.insurance.entity.DocumentKind;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.user.entity.User;

/**
 * 약관 한 건. 보험사 + 상품 + 개정판으로 식별한다.
 *
 * <p>약관은 사용자별 문서가 아니라 상품 공용 문서다. 같은 상품을 산 사용자 1000명은 글자 하나 다르지 않은 같은 약관을 본다. 그래서 분석마다 청크를 다시 만들지
 * 않고 이 행 하나를 함께 가리킨다.
 *
 * <p>증권({@link DocumentKind#CERTIFICATE})은 여기에 들어올 수 없다. 증권은 그 사람의 가입 내역이라 공유 대상이 아니고, 공용 약관으로
 * 올라가는 순간 남의 가입금액과 증권번호가 다른 사용자의 답변 근거로 쓰인다.
 */
@Getter
@Entity
@Table(
    name = "policy_terms",
    indexes = {
      @Index(name = "idx_policy_terms_insurer_product", columnList = "insurer_name,product_name"),
      @Index(name = "idx_policy_terms_owner_user_id", columnList = "owner_user_id"),
      @Index(name = "idx_policy_terms_file_hash", columnList = "file_hash")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyTerms extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "insurer_name", nullable = false, length = 200)
  private String insurerName;

  @Column(name = "product_name", nullable = false, length = 200)
  private String productName;

  /** 개정판 표기. 같은 상품이라도 가입 시점에 따라 적용받는 조항이 다르다. */
  @Column(name = "revision", length = 100)
  private String revision;

  /** 이 개정판이 효력을 갖기 시작한 날. 여러 개정판 중 하나를 고를 때의 기준이다. */
  @Column(name = "effective_date")
  private LocalDate effectiveDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "verification_status", nullable = false, length = 20)
  private TermsVerificationStatus verificationStatus = TermsVerificationStatus.UNVERIFIED;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 20)
  private TermsSource source = TermsSource.USER_UPLOAD;

  /** 사용자 업로드일 때 원본 문서. 원문을 다시 열어보거나 재파싱할 때 쓴다. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_document_id")
  private PolicyDocument sourceDocument;

  /**
   * UNVERIFIED 약관의 주인.
   *
   * <p>VERIFIED 로 승격된 뒤에도 지우지 않는다. 나중에 그 약관이 잘못된 것으로 밝혀졌을 때 어디서 들어온 것인지 남아 있어야 한다.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_user_id")
  private User ownerUser;

  /** 같은 파일을 여러 사용자가 올렸을 때 이미 만들어 둔 약관을 재사용하기 위한 지문(sha-256). */
  @Column(name = "file_hash", length = 64)
  private String fileHash;

  @Builder
  public PolicyTerms(
      String insurerName,
      String productName,
      String revision,
      LocalDate effectiveDate,
      TermsVerificationStatus verificationStatus,
      TermsSource source,
      PolicyDocument sourceDocument,
      User ownerUser,
      String fileHash) {
    this.insurerName = requireText(insurerName, "insurerName");
    this.productName = requireText(productName, "productName");
    this.revision = revision;
    this.effectiveDate = effectiveDate;
    this.verificationStatus =
        verificationStatus == null ? TermsVerificationStatus.UNVERIFIED : verificationStatus;
    this.source = source == null ? TermsSource.USER_UPLOAD : source;
    this.sourceDocument = sourceDocument;
    this.ownerUser = ownerUser;
    this.fileHash = fileHash;
    validateOwnerPresentWhenUnverified();
  }

  /**
   * 사용자가 올린 약관 문서로 UNVERIFIED 약관을 만든다.
   *
   * <p>주인은 문서를 올린 사용자다. 확인 전까지 이 약관은 그 사용자에게만 보인다.
   *
   * @param document 업로드된 약관 문서. {@link DocumentKind#TERMS}가 아니면 거부한다
   */
  public static PolicyTerms fromUserUpload(
      PolicyDocument document, String insurerName, String productName, String fileHash) {
    Objects.requireNonNull(document, "document는 필수입니다.");
    rejectCertificate(document);

    return PolicyTerms.builder()
        .insurerName(insurerName)
        .productName(productName)
        .verificationStatus(TermsVerificationStatus.UNVERIFIED)
        .source(TermsSource.USER_UPLOAD)
        .sourceDocument(document)
        .ownerUser(document.getUser())
        .fileHash(fileHash)
        .build();
  }

  /** 운영자가 보험사 공시자료에서 받아 등록하는 공용 약관. 등록 시점부터 VERIFIED 다. */
  public static PolicyTerms official(
      String insurerName, String productName, String revision, LocalDate effectiveDate) {
    return PolicyTerms.builder()
        .insurerName(insurerName)
        .productName(productName)
        .revision(revision)
        .effectiveDate(effectiveDate)
        .verificationStatus(TermsVerificationStatus.VERIFIED)
        .source(TermsSource.OFFICIAL)
        .build();
  }

  /**
   * 이 약관을 해당 사용자가 쓸 수 있는지.
   *
   * <p>VERIFIED 는 누구나, UNVERIFIED 는 올린 사람만 쓴다. 약관 조회·매칭·챗봇 검색이 모두 이 판정 하나를 거치게 해서, 범위 규칙이 여러
   * 곳으로 흩어지지 않게 한다.
   */
  public boolean isUsableBy(UUID userId) {
    if (verificationStatus == TermsVerificationStatus.VERIFIED) {
      return true;
    }
    return ownerUser != null && userId != null && userId.equals(ownerUser.getId());
  }

  /**
   * 운영자가 내용을 확인해 공용 약관으로 올린다.
   *
   * <p>{@code ownerUser}는 그대로 둔다 -- DB CHECK 는 VERIFIED 에 주인이 있어도 허용하고, 출처를 지울 이유가 없다.
   */
  public void markVerified() {
    this.verificationStatus = TermsVerificationStatus.VERIFIED;
  }

  public boolean isVerified() {
    return verificationStatus == TermsVerificationStatus.VERIFIED;
  }

  private static void rejectCertificate(PolicyDocument document) {
    if (document.getDocumentKind() != DocumentKind.TERMS) {
      throw new IllegalArgumentException(
          "약관으로 등록할 수 있는 것은 documentKind=TERMS 인 문서뿐입니다: " + document.getDocumentKind());
    }
  }

  /**
   * DB의 {@code policy_terms_unverified_requires_owner_check}와 같은 규칙을 여기서 먼저 막는다.
   *
   * <p>주인 없는 UNVERIFIED 행은 {@link #isUsableBy}에서 누구의 것도 아니게 되어, 아무도 볼 수 없는 죽은 데이터가 된다. DB까지
   * 내려가면 어느 필드가 문제인지 알기 어려우므로 만드는 자리에서 걸러 준다.
   */
  private void validateOwnerPresentWhenUnverified() {
    if (verificationStatus == TermsVerificationStatus.UNVERIFIED && ownerUser == null) {
      throw new IllegalArgumentException("UNVERIFIED 약관에는 주인(ownerUser)이 있어야 합니다.");
    }
  }

  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + "은(는) 필수입니다.");
    }
    return value;
  }
}
