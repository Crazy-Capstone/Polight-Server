package polight.server.domain.terms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
import polight.server.domain.common.entity.BaseTimeEntity;
import polight.server.domain.rag.entity.PolicyChunkClauseType;
import polight.server.domain.rag.entity.PolicyChunkSourceContentType;

/**
 * 약관 본문 청크. 검색과 근거 인용의 최소 단위다.
 *
 * <p>{@code policy_chunks}와 달리 {@code user_id}/{@code trip_id}/{@code policy_id}를 갖지 않는다. 그
 * 컬럼들은 "이 청크를 누가 검색할 수 있는가"를 청크에 박아 둔 것인데, 약관은 상품 공용 문서라 그 답이 청크가 아니라 {@link PolicyTerms}에 있다.
 * 여기에 다시 두면 공용 약관을 쓰는 사용자가 늘 때마다 청크를 복제해야 한다 -- 이 리팩토링이 없애려던 바로 그것이다.
 *
 * <p>적재는 AI 서버가 {@code rag_service} 계정으로 직접 한다. 백엔드는 읽기만 한다.
 *
 * <p>{@code clauseType}/{@code sourceContentType}은 {@code rag} 패키지의 enum을 그대로 쓴다. 같은 뜻의 값을 두 벌
 * 두면 DB CHECK 목록을 두 곳에서 맞춰야 한다. {@code policy_chunks}를 걷어낼 때 enum만 이 패키지로 옮기면 된다.
 */
@Getter
@Entity
@Table(
    name = "policy_terms_chunks",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_policy_terms_chunks_terms_chunk_index",
            columnNames = {"terms_id", "chunk_index"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyTermsChunk extends BaseTimeEntity {

  public static final int EMBEDDING_DIMENSION = 1536;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "terms_id", nullable = false)
  private PolicyTerms terms;

  @Column(name = "chunk_index", nullable = false)
  private int chunkIndex;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_content_type", nullable = false, length = 30)
  private PolicyChunkSourceContentType sourceContentType = PolicyChunkSourceContentType.TEXT;

  @Enumerated(EnumType.STRING)
  @Column(name = "clause_type", nullable = false, length = 30)
  private PolicyChunkClauseType clauseType = PolicyChunkClauseType.GENERAL;

  @Column(name = "page_start")
  private Integer pageStart;

  @Column(name = "page_end")
  private Integer pageEnd;

  @Column(name = "section_title", length = 500)
  private String sectionTitle;

  /** "제3관 제12조 제2항" 같은 조항 위치. 담보의 근거 조항을 찾을 때와 답변에 출처를 표시할 때 쓴다. */
  @Column(name = "clause_path", length = 300)
  private String clausePath;

  @Column(name = "coverage_category", length = 100)
  private String coverageCategory;

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
  public PolicyTermsChunk(
      PolicyTerms terms,
      Integer chunkIndex,
      PolicyChunkSourceContentType sourceContentType,
      PolicyChunkClauseType clauseType,
      Integer pageStart,
      Integer pageEnd,
      String sectionTitle,
      String clausePath,
      String coverageCategory,
      String content,
      String summary,
      float[] embedding,
      Integer charCount) {
    this.terms = Objects.requireNonNull(terms, "terms는 필수입니다.");
    this.chunkIndex = Objects.requireNonNull(chunkIndex, "chunkIndex는 필수입니다.");
    this.sourceContentType =
        sourceContentType == null ? PolicyChunkSourceContentType.TEXT : sourceContentType;
    this.clauseType = clauseType == null ? PolicyChunkClauseType.GENERAL : clauseType;
    this.pageStart = pageStart;
    this.pageEnd = pageEnd;
    this.sectionTitle = sectionTitle;
    this.clausePath = clausePath;
    this.coverageCategory = coverageCategory;
    this.content = Objects.requireNonNull(content, "content는 필수입니다.");
    this.summary = summary;
    this.embedding = embedding;
    this.charCount = charCount == null ? content.length() : charCount;
  }
}
