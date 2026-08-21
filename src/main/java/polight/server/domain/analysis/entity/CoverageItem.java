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
import polight.server.domain.terms.entity.PolicyTermsCoverage;

@Getter
@Entity
@Table(
    name = "coverage_items",
    indexes = {
      @Index(name = "idx_coverage_items_analysis_sort", columnList = "analysis_result_id,sort_order"),
      @Index(name = "idx_coverage_items_terms_coverage_id", columnList = "terms_coverage_id")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverageItem extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "analysis_result_id", nullable = false)
  private AnalysisResult analysisResult;

  /**
   * 이 가입 담보에 적용되는 약관의 보장 규칙.
   *
   * <p>보장 상세 화면은 이 연결을 타고 두 영역을 합친다 -- 가입 여부와 가입금액은 이 엔티티에서, 면책·청구서류·세부한도는 규칙 쪽에서 가져온다.
   *
   * <p>null인 것이 정상 갈래다. 약관을 못 찾았거나(분석의 {@code matchedTerms}가 없거나), 찾았어도 증권 담보명이 약관의 어느 규칙과도
   * 맞지 않을 수 있다. 그때는 가입 정보만 내려간다. 억지로 붙이면 다른 담보의 면책 조항을 보여주게 되고, 사용자는 그것을 보고 받을 수 있는 보험금을
   * 포기한다.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "terms_coverage_id")
  private PolicyTermsCoverage termsCoverage;

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
  /**
   * 적용받는 약관 규칙을 연결한다.
   *
   * <p>{@code null}을 넣어 끊을 수 있다. 재분석으로 담보명이나 연결된 약관이 달라지면 이전 규칙은 더 이상 이 담보의 것이 아니다.
   */
  public void linkTermsCoverage(PolicyTermsCoverage termsCoverage) {
    this.termsCoverage = termsCoverage;
  }

}
