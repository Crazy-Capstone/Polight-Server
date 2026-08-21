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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "coverage_detail_items",
    indexes = @Index(name = "idx_coverage_detail_items_terms_coverage_sort", columnList = "terms_coverage_id,sort_order"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverageDetailItem {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "terms_coverage_id", nullable = false)
  private PolicyTermsCoverage termsCoverage;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(length = 500)
  private String subtitle;

  @Column(name = "is_covered", nullable = false)
  private boolean covered;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Builder
  public CoverageDetailItem(PolicyTermsCoverage termsCoverage, String title, String subtitle, boolean covered, Integer sortOrder) {
    this.termsCoverage = termsCoverage;
    this.title = title;
    this.subtitle = subtitle;
    this.covered = covered;
    this.sortOrder = sortOrder == null ? 0 : sortOrder;
  }
}
