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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import polight.server.domain.common.entity.BaseTimeEntity;
import polight.server.domain.insurance.entity.DocumentParseStatus;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.policy.entity.Policy;

@Getter
@Entity
@Table(
    name = "analysis_results",
    indexes = {
      @Index(name = "idx_analysis_results_document_id", columnList = "document_id"),
      @Index(name = "idx_analysis_results_policy_id", columnList = "policy_id"),
      @Index(name = "idx_analysis_results_parse_status", columnList = "parse_status")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisResult extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "document_id", nullable = false)
  private PolicyDocument document;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "policy_id")
  private Policy policy;

  @Column(columnDefinition = "TEXT")
  private String summary;

  @Column(name = "raw_result_json", columnDefinition = "TEXT")
  private String rawResultJson;

  @Column(name = "accuracy_score")
  private Float accuracyScore;

  @Enumerated(EnumType.STRING)
  @Column(name = "parse_status", nullable = false, length = 20)
  private DocumentParseStatus parseStatus = DocumentParseStatus.PROCESSING;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Column(name = "analyzed_at", nullable = false)
  private LocalDateTime analyzedAt;

  @Builder
  public AnalysisResult(
      PolicyDocument document,
      Policy policy,
      String summary,
      String rawResultJson,
      Float accuracyScore,
      DocumentParseStatus parseStatus,
      String errorMessage,
      LocalDateTime analyzedAt) {
    this.document = document;
    this.policy = policy;
    this.summary = summary;
    this.rawResultJson = rawResultJson;
    this.accuracyScore = accuracyScore;
    this.parseStatus = parseStatus == null ? DocumentParseStatus.PROCESSING : parseStatus;
    this.errorMessage = errorMessage;
    this.analyzedAt = analyzedAt;
  }

  @PrePersist
  void prePersist() {
    if (analyzedAt == null) {
      analyzedAt = LocalDateTime.now();
    }
  }
}
