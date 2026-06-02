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
@Table(name = "coverage_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverageItem {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "analysis_result_id", nullable = false)
  private AnalysisResult analysisResult;

  @Column(length = 100)
  private String category;

  @Column(name = "item_name", nullable = false, length = 200)
  private String itemName;

  @Column(name = "one_line_summary", length = 500)
  private String oneLineSummary;

  @Column(name = "is_covered", nullable = false)
  private boolean covered;

  @Column(name = "coverage_status", nullable = false, length = 20)
  private String coverageStatus = "not_covered";

  @Column(name = "limit_amount")
  private Long limitAmount;

  @Column(name = "limit_currency", length = 10)
  private String limitCurrency = "KRW";

  @Column(columnDefinition = "TEXT")
  private String conditions;

  @Builder
  public CoverageItem(
      AnalysisResult analysisResult,
      String category,
      String itemName,
      String oneLineSummary,
      boolean covered,
      String coverageStatus,
      Long limitAmount,
      String limitCurrency,
      String conditions) {
    this.analysisResult = analysisResult;
    this.category = category;
    this.itemName = itemName;
    this.oneLineSummary = oneLineSummary;
    this.covered = covered;
    this.coverageStatus = coverageStatus == null ? "not_covered" : coverageStatus;
    this.limitAmount = limitAmount;
    this.limitCurrency = limitCurrency == null ? "KRW" : limitCurrency;
    this.conditions = conditions;
  }
}
