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
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.common.entity.BaseTimeEntity;
import polight.server.domain.insurance.entity.PolicyDocument;
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
      @Index(name = "idx_policy_chunks_user_document", columnList = "user_id,document_id"),
      // AI 서버는 document_id 만으로 스코프를 좁힌다. 위 복합 인덱스는 선두 컬럼(user_id)이
      // 조건에 없으면 쓰이지 않아 단독 인덱스가 따로 필요하다.
      @Index(name = "idx_policy_chunks_document_id", columnList = "document_id")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyChunk extends BaseTimeEntity {

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

  // embedding 컬럼은 매핑하지 않는다.
  //
  // pgvector 의 vector(1536) 인데, 이 프로젝트에는 hibernate-vector 가 없어
  // @JdbcTypeCode(SqlTypes.VECTOR) 가 해석되지 않는다. Hibernate 는 float[] 을
  // VARBINARY(자바 직렬화)로 폴백해 읽으려 하고, 실제로 오는 값은 "[0.2,..." 라는
  // pgvector 텍스트라 엔티티를 만드는 순간 SerializationException 으로 터진다.
  //
  // 의존성을 넣어 제대로 매핑할 수도 있지만 그럴 이유가 없다. 벡터 검색은 AI 서버가
  // 하고 백엔드는 이 값을 읽지도 쓰지도 않는다. 매핑하면 청크마다 1536개 float 을
  // 함께 실어 오기만 한다. 컬럼은 DB 에 그대로 있고 AI 서버가 계속 쓴다 --
  // ddl-auto=validate 는 매핑되지 않은 컬럼을 문제 삼지 않는다.

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
      Integer charCount) {
    this.analysisResult = Objects.requireNonNull(analysisResult, "analysisResult는 필수입니다.");
    this.document = Objects.requireNonNull(analysisResult.getDocument(), "analysisResult.document는 필수입니다.");
    this.user = Objects.requireNonNull(document.getUser(), "document.user는 필수입니다.");
    this.trip = resolveTrip();
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
    this.charCount = charCount == null ? content.length() : charCount;
  }

  /**
   * 이 청크를 어느 여행의 것으로 볼지.
   *
   * <p>예전에는 문서 → 보험계약 → 여행 순으로 훑었다. {@code policies}를 없애면서 경로가 문서 하나로 줄었다. 어차피
   * {@code policies}에는 행이 만들어진 적이 없어 그 경로는 늘 null을 돌려주고 있었다.
   */
  private Trip resolveTrip() {
    return document.getTrip();
  }
}
