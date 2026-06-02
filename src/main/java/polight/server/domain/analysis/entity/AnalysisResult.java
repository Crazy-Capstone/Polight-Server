package polight.server.domain.analysis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import polight.server.domain.insurance.entity.InsuranceDocument;

@Getter
@Entity
@Table(name = "analysis_results")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisResult {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "document_id", nullable = false)
  private InsuranceDocument document;

  @Column(columnDefinition = "TEXT")
  private String summary;

  @Column(name = "accuracy_score")
  private Float accuracyScore;

  @Column(name = "parse_status", nullable = false, length = 20)
  private String parseStatus = "pending";

  @Column(name = "analyzed_at", nullable = false)
  private LocalDateTime analyzedAt;

  @Builder
  public AnalysisResult(
      InsuranceDocument document,
      String summary,
      Float accuracyScore,
      String parseStatus,
      LocalDateTime analyzedAt) {
    this.document = document;
    this.summary = summary;
    this.accuracyScore = accuracyScore;
    this.parseStatus = parseStatus == null ? "pending" : parseStatus;
    this.analyzedAt = analyzedAt;
  }

  @PrePersist
  void prePersist() {
    if (analyzedAt == null) {
      analyzedAt = LocalDateTime.now();
    }
  }
}
