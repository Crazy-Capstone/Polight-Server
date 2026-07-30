package polight.server.domain.rag.entity;

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
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.common.entity.BaseTimeEntity;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.policy.entity.Policy;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.user.entity.User;

@Getter
@Entity
@Table(
    name = "policy_chunks",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_policy_chunks_analysis_chunk_index",
            columnNames = {"analysis_result_id", "chunk_index"}),
    indexes = {
      @Index(name = "idx_policy_chunks_user_trip", columnList = "user_id,trip_id"),
      @Index(name = "idx_policy_chunks_user_policy", columnList = "user_id,policy_id"),
      @Index(name = "idx_policy_chunks_user_document", columnList = "user_id,document_id"),
      @Index(name = "idx_policy_chunks_coverage_category", columnList = "coverage_category")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyChunk extends BaseTimeEntity {

  public static final int EMBEDDING_DIMENSION = 1536;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "analysis_result_id", nullable = false)
  private AnalysisResult analysisResult;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trip_id")
  private Trip trip;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "policy_id")
  private Policy policy;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "document_id", nullable = false)
  private PolicyDocument document;

  @Column(name = "chunk_index", nullable = false)
  private int chunkIndex;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_content_type", nullable = false, length = 30)
  private PolicyChunkSourceContentType sourceContentType = PolicyChunkSourceContentType.TEXT;

  @Column(name = "page_start")
  private Integer pageStart;

  @Column(name = "page_end")
  private Integer pageEnd;

  @Column(name = "section_title", length = 500)
  private String sectionTitle;

  @Column(name = "clause_path", length = 300)
  private String clausePath;

  @Column(name = "coverage_category", length = 100)
  private String coverageCategory;

  @Enumerated(EnumType.STRING)
  @Column(name = "clause_type", nullable = false, length = 30)
  private PolicyChunkClauseType clauseType = PolicyChunkClauseType.GENERAL;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(columnDefinition = "TEXT")
  private String summary;

  @JdbcTypeCode(SqlTypes.VECTOR)
  @Array(length = EMBEDDING_DIMENSION)
  @Column(name = "embedding", columnDefinition = "vector(1536)")
  private float[] embedding;

  @Column(name = "char_count", nullable = false)
  private int charCount;

  @Builder
  public PolicyChunk(
      AnalysisResult analysisResult,
      Integer chunkIndex,
      PolicyChunkSourceContentType sourceContentType,
      Integer pageStart,
      Integer pageEnd,
      String sectionTitle,
      String clausePath,
      String coverageCategory,
      PolicyChunkClauseType clauseType,
      String content,
      String summary,
      float[] embedding,
      Integer charCount) {
    this.analysisResult = Objects.requireNonNull(analysisResult, "analysisResult는 필수입니다.");
    this.document = Objects.requireNonNull(analysisResult.getDocument(), "analysisResult.document는 필수입니다.");
    this.user = Objects.requireNonNull(document.getUser(), "document.user는 필수입니다.");
    this.policy = resolvePolicy(analysisResult);
    this.trip = resolveTrip(analysisResult, policy);
    this.chunkIndex = Objects.requireNonNull(chunkIndex, "chunkIndex는 필수입니다.");
    this.sourceContentType = sourceContentType == null ? PolicyChunkSourceContentType.TEXT : sourceContentType;
    this.pageStart = pageStart;
    this.pageEnd = pageEnd;
    this.sectionTitle = sectionTitle;
    this.clausePath = clausePath;
    this.coverageCategory = coverageCategory;
    this.clauseType = clauseType == null ? PolicyChunkClauseType.GENERAL : clauseType;
    this.content = Objects.requireNonNull(content, "content는 필수입니다.");
    this.summary = summary;
    this.embedding = embedding;
    this.charCount = charCount == null ? content.length() : charCount;
  }

  private Policy resolvePolicy(AnalysisResult analysisResult) {
    if (document.getPolicy() != null) {
      return document.getPolicy();
    }
    return analysisResult.getPolicy();
  }

  private Trip resolveTrip(AnalysisResult analysisResult, Policy resolvedPolicy) {
    if (document.getTrip() != null) {
      return document.getTrip();
    }
    if (document.getPolicy() != null) {
      return document.getPolicy().getTrip();
    }
    if (resolvedPolicy != null) {
      return resolvedPolicy.getTrip();
    }
    return null;
  }
}
