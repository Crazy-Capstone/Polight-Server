package polight.server.domain.terms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import polight.server.domain.common.entity.BaseTimeEntity;

/**
 * 약관에 적힌 보장 규칙 하나. "이 상품은 이런 보장을 한다"는 상품의 사실이다.
 *
 * <p>사용자의 가입 담보({@code CoverageItem})와 반드시 구분해야 한다. 비슷해 보이지만 공유 범위가 정반대다.
 *
 * <table>
 *   <tr><th></th><th>PolicyTermsCoverage</th><th>CoverageItem</th></tr>
 *   <tr><td>뜻</td><td>약관에 있는 보장 규칙</td><td>그 사람이 가입한 담보</td></tr>
 *   <tr><td>공유</td><td>같은 상품 가입자 전원</td><td>그 사용자 한 명</td></tr>
 *   <tr><td>출처</td><td>약관 본문</td><td>증권 보장내용 표</td></tr>
 *   <tr><td>금액</td><td>약관에 인쇄된 표기({@code limitLabel})</td><td>실제 가입금액</td></tr>
 * </table>
 *
 * <p>면책 조항·청구 서류·세부 한도는 모두 이쪽에 달린다. 같은 상품을 산 사람에게 모두 같은 내용이라 사용자마다 복제할 이유가 없고, 복제하면 약관이 개정될 때
 * 일부만 고쳐져 어떤 사용자는 낡은 면책 조항을 보게 된다.
 *
 * <p>정수 금액 컬럼을 두지 않는 것은 의도다. 숫자로 확정된 금액은 가입 사실이고, 그것은 {@code CoverageItem}에만 있어야 한다. 여기에 두면 약관
 * 예시값이 사용자의 가입금액으로 읽힐 자리가 다시 생긴다.
 */
@Getter
@Entity
@Table(
    name = "policy_terms_coverages",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_policy_terms_coverages_terms_sort",
            columnNames = {"terms_id", "sort_order"}),
    indexes =
        @Index(name = "idx_policy_terms_coverages_terms_title", columnList = "terms_id,title"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyTermsCoverage extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "terms_id", nullable = false)
  private PolicyTerms terms;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(length = 500)
  private String subtitle;

  @Column(length = 100)
  private String category;

  /** 약관에 적힌 한도 표기 그대로. "가입금액의 100%", "1사고당 1억원" 같은 문구가 온다. */
  @Column(name = "limit_label", length = 100)
  private String limitLabel;

  @Column(columnDefinition = "TEXT")
  private String conditions;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Builder
  public PolicyTermsCoverage(
      PolicyTerms terms,
      String title,
      String subtitle,
      String category,
      String limitLabel,
      String conditions,
      Integer sortOrder) {
    this.terms = Objects.requireNonNull(terms, "terms는 필수입니다.");
    this.title = Objects.requireNonNull(title, "title은 필수입니다.");
    this.subtitle = subtitle;
    this.category = category;
    this.limitLabel = limitLabel;
    this.conditions = conditions;
    this.sortOrder = sortOrder == null ? 0 : sortOrder;
  }

  /** 이 규칙이 속한 약관을 해당 사용자가 쓸 수 있는지. 판정은 {@link PolicyTerms#isUsableBy}에 위임한다. */
  public boolean isUsableBy(UUID userId) {
    return terms != null && terms.isUsableBy(userId);
  }
}
