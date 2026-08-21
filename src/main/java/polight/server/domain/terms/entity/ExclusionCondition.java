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
    name = "exclusion_conditions",
    indexes = @Index(name = "idx_exclusion_conditions_terms_coverage_sort", columnList = "terms_coverage_id,sort_order"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExclusionCondition extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "terms_coverage_id", nullable = false)
  private PolicyTermsCoverage termsCoverage;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "source_text", columnDefinition = "TEXT")
  private String sourceText;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ExclusionConditionSeverity severity = ExclusionConditionSeverity.GENERAL;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Builder
  public ExclusionCondition(
      PolicyTermsCoverage termsCoverage,
      String title,
      String description,
      String sourceText,
      ExclusionConditionSeverity severity,
      Integer sortOrder) {
    this.termsCoverage = Objects.requireNonNull(termsCoverage, "termsCoverage는 필수입니다.");
    this.title = Objects.requireNonNull(title, "title은 필수입니다.");
    this.description = description;
    this.sourceText = sourceText;
    this.severity = severity == null ? ExclusionConditionSeverity.GENERAL : severity;
    this.sortOrder = sortOrder == null ? 0 : sortOrder;
  }
}
