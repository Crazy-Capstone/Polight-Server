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
import jakarta.persistence.UniqueConstraint;
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
    uniqueConstraints = @UniqueConstraint(name = "uk_analysis_results_document_id", columnNames = "document_id"),
    indexes = {
      @Index(name = "idx_analysis_results_policy_id", columnList = "policy_id"),
      @Index(name = "idx_analysis_results_status", columnList = "status")
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
  @Column(nullable = false, length = 20)
  private AnalysisStatus status = AnalysisStatus.PROCESSING;

  @Column(name = "embedding_model", length = 100)
  private String embeddingModel;

  @Column(name = "embedding_dimension")
  private Integer embeddingDimension;

  /**
   * 담보 목록이 증권의 보장내용 표 전체인지 여부. AI 서버가 콜백으로 알려준다.
   *
   * <p>false면 "목록에 없다"를 "가입하지 않았다"로 단정할 수 없다. 추출이 빠뜨린 것일 수 있다.
   */
  @Column(name = "coverages_complete", nullable = false)
  private boolean coveragesComplete;

  @Column(name = "started_at", nullable = false)
  private LocalDateTime startedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Column(name = "failure_reason", columnDefinition = "TEXT")
  private String failureReason;

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
      String embeddingModel,
      Integer embeddingDimension,
      LocalDateTime startedAt,
      LocalDateTime completedAt,
      String failureReason,
      LocalDateTime analyzedAt) {
    this.document = document;
    this.policy = policy;
    this.summary = summary;
    this.rawResultJson = rawResultJson;
    this.accuracyScore = accuracyScore;
    this.status = status == null ? AnalysisStatus.PROCESSING : status;
    this.embeddingModel = embeddingModel;
    this.embeddingDimension = embeddingDimension;
    this.startedAt = startedAt;
    this.completedAt = completedAt;
    this.failureReason = failureReason;
    this.analyzedAt = analyzedAt;
  }

  /**
   * AI 서버 콜백으로 받은 산출물을 반영하고 완료로 표시한다.
   *
   * <p>{@code rawResultJson}에는 콜백 본문 전체를 넣는다. {@code insurerName}/{@code productName}처럼 아직
   * 저장할 컬럼이 없는 필드가 유실되지 않게 하려는 것이다. 보험기간이 콜백에 실려 오면 여기서 꺼내 {@code policies}를
   * 만들 수 있다.
   */
  public void completeWith(
      String summary,
      String rawResultJson,
      String embeddingModel,
      Integer embeddingDimension,
      Float accuracyScore,
      boolean coveragesComplete,
      LocalDateTime completedAt) {
    this.summary = summary;
    this.rawResultJson = rawResultJson;
    this.embeddingModel = embeddingModel;
    this.embeddingDimension = embeddingDimension;
    this.accuracyScore = accuracyScore;
    this.coveragesComplete = coveragesComplete;
    markCompleted(completedAt);
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
