package polight.server.domain.analysis.entity;

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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "sub_coverage_limits",
    indexes = @Index(name = "idx_sub_coverage_limits_coverage_sort", columnList = "coverage_item_id,sort_order"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubCoverageLimit {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "coverage_item_id", nullable = false)
  private CoverageItem coverageItem;

  @Column(nullable = false, length = 100)
  private String label;

  @Column(nullable = false, length = 200)
  private String value;

  @Column(name = "limit_amount")
  private Long limitAmount;

  @Column(name = "limit_currency", length = 10)
  private String limitCurrency = "KRW";

  @Column(length = 500)
  private String description;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Builder
  public SubCoverageLimit(
      CoverageItem coverageItem,
      String label,
      String value,
      Long limitAmount,
      String limitCurrency,
      String description,
      Integer sortOrder) {
    this.coverageItem = coverageItem;
    this.label = label;
    this.value = value;
    this.limitAmount = limitAmount;
    this.limitCurrency = limitCurrency == null ? "KRW" : limitCurrency;
    this.description = description;
    this.sortOrder = sortOrder == null ? 0 : sortOrder;
  }
}
