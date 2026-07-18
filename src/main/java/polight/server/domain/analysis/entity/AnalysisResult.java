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
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.policy.entity.Policy;

@Getter
@Entity
@Table(
    name = "analysis_results",
    indexes = {
      @Index(name = "idx_analysis_results_document_id", columnList = "document_id"),
      @Index(name = "idx_analysis_results_policy_id", columnList = "policy_id"),
      @Index(name = "idx_analysis_results_status", columnList = "status"),
      @Index(name = "idx_analysis_results_document_active", columnList = "document_id,is_active,status"),
      @Index(name = "idx_analysis_results_policy_active", columnList = "policy_id,is_active,status")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisResult extends BaseTimeEntity {

  private static final String DEFAULT_ANALYSIS_VERSION = "v1";

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
  @Column(nullable = false, length = 20)
  private AnalysisStatus status = AnalysisStatus.PROCESSING;

  @Column(name = "analysis_version", nullable = false, length = 50)
  private String analysisVersion = DEFAULT_ANALYSIS_VERSION;

  @Column(name = "chunking_version", length = 50)
  private String chunkingVersion;

  @Column(name = "embedding_model", length = 100)
  private String embeddingModel;

  @Column(name = "embedding_dimension")
  private Integer embeddingDimension;

  @Column(name = "started_at", nullable = false)
  private LocalDateTime startedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Column(name = "failure_reason", columnDefinition = "TEXT")
  private String failureReason;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(name = "analyzed_at")
  private LocalDateTime analyzedAt;

  @Builder
  public AnalysisResult(
      PolicyDocument document,
      Policy policy,
      String summary,
      String rawResultJson,
      Float accuracyScore,
      AnalysisStatus status,
      String analysisVersion,
      String chunkingVersion,
      String embeddingModel,
      Integer embeddingDimension,
      LocalDateTime startedAt,
      LocalDateTime completedAt,
      String failureReason,
      boolean active,
      LocalDateTime analyzedAt) {
    this.document = document;
    this.policy = policy;
    this.summary = summary;
    this.rawResultJson = rawResultJson;
    this.accuracyScore = accuracyScore;
    this.status = status == null ? AnalysisStatus.PROCESSING : status;
    this.analysisVersion =
        analysisVersion == null || analysisVersion.isBlank() ? DEFAULT_ANALYSIS_VERSION : analysisVersion;
    this.chunkingVersion = chunkingVersion;
    this.embeddingModel = embeddingModel;
    this.embeddingDimension = embeddingDimension;
    this.startedAt = startedAt;
    this.completedAt = completedAt;
    this.failureReason = failureReason;
    this.active = active;
    this.analyzedAt = analyzedAt;
  }

  public void markCompleted(LocalDateTime completedAt) {
    this.status = AnalysisStatus.COMPLETED;
    this.completedAt = completedAt == null ? LocalDateTime.now() : completedAt;
    this.analyzedAt = this.completedAt;
    this.failureReason = null;
  }

  public void markFailed(String failureReason, LocalDateTime completedAt) {
    this.status = AnalysisStatus.FAILED;
    this.completedAt = completedAt == null ? LocalDateTime.now() : completedAt;
    this.failureReason = failureReason;
    this.active = false;
  }

  public void activate() {
    if (status != AnalysisStatus.COMPLETED) {
      throw new IllegalStateException("완료된 분석 결과만 활성화할 수 있습니다.");
    }
    this.active = true;
  }

  public void deactivate() {
    this.active = false;
  }

  @PrePersist
  void prePersist() {
    if (startedAt == null) {
      startedAt = LocalDateTime.now();
    }
    if (analyzedAt == null) {
      analyzedAt = completedAt;
    }
  }
}
