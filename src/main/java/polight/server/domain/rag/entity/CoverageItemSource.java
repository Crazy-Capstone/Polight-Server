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

@Getter
@Entity
@Table(
    name = "coverage_item_sources",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_coverage_item_sources_item_chunk_role",
            columnNames = {"coverage_item_id", "policy_chunk_id", "source_role"}),
    indexes = @Index(name = "idx_coverage_item_sources_policy_chunk_id", columnList = "policy_chunk_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverageItemSource extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "coverage_item_id", nullable = false)
  private CoverageItem coverageItem;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "policy_chunk_id", nullable = false)
  private PolicyChunk policyChunk;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_role", nullable = false, length = 30)
  private CoverageItemSourceRole sourceRole = CoverageItemSourceRole.PRIMARY;

  @Column(name = "quote_text", columnDefinition = "TEXT")
  private String quoteText;

  @Builder
  public CoverageItemSource(
      CoverageItem coverageItem,
      PolicyChunk policyChunk,
      CoverageItemSourceRole sourceRole,
      String quoteText) {
    this.coverageItem = Objects.requireNonNull(coverageItem, "coverageItem은 필수입니다.");
    this.policyChunk = Objects.requireNonNull(policyChunk, "policyChunk는 필수입니다.");
    validateSameAnalysisResult(coverageItem, policyChunk);
    this.sourceRole = sourceRole == null ? CoverageItemSourceRole.PRIMARY : sourceRole;
    this.quoteText = quoteText;
  }

  private void validateSameAnalysisResult(CoverageItem coverageItem, PolicyChunk policyChunk) {
    AnalysisResult coverageAnalysisResult = coverageItem.getAnalysisResult();
    AnalysisResult chunkAnalysisResult = policyChunk.getAnalysisResult();

    if (coverageAnalysisResult == chunkAnalysisResult) {
      return;
    }

    UUID coverageAnalysisResultId = coverageAnalysisResult.getId();
    UUID chunkAnalysisResultId = chunkAnalysisResult.getId();

    if (coverageAnalysisResultId == null
        || chunkAnalysisResultId == null
        || !coverageAnalysisResultId.equals(chunkAnalysisResultId)) {
      throw new IllegalArgumentException("coverageItem과 policyChunk는 같은 analysisResult에 속해야 합니다.");
    }
  }
}
