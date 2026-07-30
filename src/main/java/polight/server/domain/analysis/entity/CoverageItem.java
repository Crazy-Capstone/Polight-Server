package polight.server.domain.analysis.entity;

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
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import polight.server.domain.common.entity.BaseTimeEntity;

@Getter
@Entity
@Table(
    name = "coverage_items",
    indexes = {
      @Index(name = "idx_coverage_items_analysis_sort", columnList = "analysis_result_id,sort_order")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverageItem extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "analysis_result_id", nullable = false)
  private AnalysisResult analysisResult;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(length = 500)
  private String subtitle;

  @Column(length = 100)
  private String category;

  @Column(name = "limit_label", length = 100)
  private String limitLabel;

  @Column(name = "is_covered", nullable = false)
  private boolean covered;

  @Enumerated(EnumType.STRING)
  @Column(name = "coverage_status", nullable = false, length = 20)
  private CoverageStatus coverageStatus = CoverageStatus.NOT_COVERED;

  @Column(name = "limit_amount")
  private Long limitAmount;

  @Column(name = "limit_currency", length = 10)
  private String limitCurrency = "KRW";

  @Column(columnDefinition = "TEXT")
  private String conditions;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Builder
  public CoverageItem(
      AnalysisResult analysisResult,
      String title,
      String subtitle,
      String category,
      String limitLabel,
      boolean covered,
      CoverageStatus coverageStatus,
      Long limitAmount,
      String limitCurrency,
      String conditions,
      Integer sortOrder) {
    this.analysisResult = Objects.requireNonNull(analysisResult, "analysisResult는 필수입니다.");
    this.title = title;
    this.subtitle = subtitle;
    this.category = category;
    this.limitLabel = limitLabel;
    this.covered = covered;
    this.coverageStatus = coverageStatus == null ? CoverageStatus.NOT_COVERED : coverageStatus;
    this.limitAmount = limitAmount;
    this.limitCurrency = limitCurrency == null ? "KRW" : limitCurrency;
    this.conditions = conditions;
    this.sortOrder = sortOrder == null ? 0 : sortOrder;
  }
}
