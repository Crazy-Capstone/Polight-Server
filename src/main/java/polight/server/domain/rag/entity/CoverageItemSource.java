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
import polight.server.domain.analysis.entity.CoverageItem;
import polight.server.domain.common.entity.BaseTimeEntity;
import polight.server.domain.terms.entity.PolicyTerms;
import polight.server.domain.terms.entity.PolicyTermsChunk;

/**
 * 담보 하나와 그 근거가 되는 약관 조항의 연결.
 *
 * <p>증권에는 "해외여행중 상해의료비 3,000만원" 같은 표만 있다. 무엇이 보장되고 무엇이 면책이며 어떤 서류를 내야 하는지는 약관에만 있다. 그래서 근거로
 * 가리키는 것은 증권 청크가 아니라 약관 청크({@link PolicyTermsChunk})다.
 */
@Getter
@Entity
@Table(
    name = "coverage_item_sources",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_coverage_item_sources_item_chunk_role",
            columnNames = {"coverage_item_id", "terms_chunk_id", "source_role"}),
    indexes = @Index(name = "idx_coverage_item_sources_terms_chunk_id", columnList = "terms_chunk_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverageItemSource extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "coverage_item_id", nullable = false)
  private CoverageItem coverageItem;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "terms_chunk_id", nullable = false)
  private PolicyTermsChunk termsChunk;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_role", nullable = false, length = 30)
  private CoverageItemSourceRole sourceRole = CoverageItemSourceRole.PRIMARY;

  @Column(name = "quote_text", columnDefinition = "TEXT")
  private String quoteText;

  @Builder
  public CoverageItemSource(
      CoverageItem coverageItem,
      PolicyTermsChunk termsChunk,
      CoverageItemSourceRole sourceRole,
      String quoteText) {
    this.coverageItem = Objects.requireNonNull(coverageItem, "coverageItem은 필수입니다.");
    this.termsChunk = Objects.requireNonNull(termsChunk, "termsChunk는 필수입니다.");
    validateChunkBelongsToMatchedTerms(coverageItem, termsChunk);
    this.sourceRole = sourceRole == null ? CoverageItemSourceRole.PRIMARY : sourceRole;
    this.quoteText = quoteText;
  }

  /**
   * 근거 조항은 "그 분석에 연결된 약관"의 조항이어야 한다.
   *
   * <p>예전 불변식은 "담보와 청크가 같은 분석에 속할 것"이었다. 약관 청크가 분석에 묶여 있던 시절의 규칙이라 이제 성립하지 않는다 -- 약관은 상품 공용
   * 문서라 어느 한 분석의 것이 아니다.
   *
   * <p>대신 확인할 것은 이것이다: 이 담보가 나온 분석이 가리키는 약관과, 근거로 달려는 조항이 속한 약관이 같은가.
   *
   * <p>이 검사가 막는 것은 <b>다른 상품의 약관 조항을 근거로 붙이는 일</b>이다. 그렇게 붙으면 화면에는 그럴듯한 조항이 인용되지만 실제로는 사용자가 가입한
   * 상품과 무관한 문장이다. 사용자는 그것을 보고 청구를 포기하거나, 되지 않을 청구를 준비한다. 조용히 틀리는 종류라 사람이 대조하기 전에는 드러나지 않는다.
   *
   * <p>약관이 연결되지 않은 분석에는 근거를 달 수 없다. 어느 약관의 조항이 맞는지 확인할 기준 자체가 없기 때문이다.
   */
  private void validateChunkBelongsToMatchedTerms(
      CoverageItem coverageItem, PolicyTermsChunk termsChunk) {
    AnalysisResult analysisResult = coverageItem.getAnalysisResult();
    PolicyTerms matchedTerms = analysisResult == null ? null : analysisResult.getMatchedTerms();

    if (matchedTerms == null) {
      throw new IllegalArgumentException("약관이 연결되지 않은 분석의 담보에는 근거 조항을 달 수 없습니다.");
    }

    PolicyTerms chunkTerms = termsChunk.getTerms();
    if (matchedTerms == chunkTerms) {
      return;
    }

    UUID matchedTermsId = matchedTerms.getId();
    UUID chunkTermsId = chunkTerms == null ? null : chunkTerms.getId();

    if (matchedTermsId == null || chunkTermsId == null || !matchedTermsId.equals(chunkTermsId)) {
      throw new IllegalArgumentException("근거 조항은 그 분석에 연결된 약관의 조항이어야 합니다.");
    }
  }
}
