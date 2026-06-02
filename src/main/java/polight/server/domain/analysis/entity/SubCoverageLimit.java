package polight.server.domain.analysis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "sub_coverage_limits")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubCoverageLimit {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "coverage_item_id", nullable = false)
  private CoverageItem coverageItem;

  @Column(name = "sub_item_name", nullable = false, length = 200)
  private String subItemName;

  @Column(name = "limit_amount")
  private Long limitAmount;

  @Column(name = "limit_currency", length = 10)
  private String limitCurrency = "KRW";

  @Column(length = 500)
  private String description;

  @Builder
  public SubCoverageLimit(
      CoverageItem coverageItem,
      String subItemName,
      Long limitAmount,
      String limitCurrency,
      String description) {
    this.coverageItem = coverageItem;
    this.subItemName = subItemName;
    this.limitAmount = limitAmount;
    this.limitCurrency = limitCurrency == null ? "KRW" : limitCurrency;
    this.description = description;
  }
}
