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

@Getter
@Entity
@Table(
    name = "policy_chunks",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_policy_chunks_analysis_chunk_index",
            columnNames = {"analysis_result_id", "chunk_index"}),
    indexes = @Index(name = "idx_policy_chunks_coverage_category", columnList = "coverage_category"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyChunk extends BaseTimeEntity {

  public static final int EMBEDDING_DIMENSION = 1536;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "analysis_result_id", nullable = false)
  private AnalysisResult analysisResult;

  @Column(name = "chunk_index", nullable = false)
  private int chunkIndex;

  @Enumerated(EnumType.STRING)
  @Column(name = "content_type", nullable = false, length = 30)
  private PolicyChunkContentType contentType = PolicyChunkContentType.TEXT;

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

  @Column(name = "coverage_type", length = 100)
  private String coverageType;

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
      PolicyChunkContentType contentType,
      Integer pageStart,
      Integer pageEnd,
      String sectionTitle,
      String clausePath,
      String coverageCategory,
      String coverageType,
      String content,
      String summary,
      float[] embedding,
      Integer charCount) {
    this.analysisResult = Objects.requireNonNull(analysisResult, "analysisResult는 필수입니다.");
    this.chunkIndex = Objects.requireNonNull(chunkIndex, "chunkIndex는 필수입니다.");
    this.contentType = contentType == null ? PolicyChunkContentType.TEXT : contentType;
    this.pageStart = pageStart;
    this.pageEnd = pageEnd;
    this.sectionTitle = sectionTitle;
    this.clausePath = clausePath;
    this.coverageCategory = coverageCategory;
    this.coverageType = coverageType;
    this.content = Objects.requireNonNull(content, "content는 필수입니다.");
    this.summary = summary;
    this.embedding = embedding;
    this.charCount = charCount == null ? content.length() : charCount;
  }
}
