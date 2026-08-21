package polight.server.domain.terms.entity;

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
import polight.server.domain.common.entity.BaseTimeEntity;

/**
 * 약관의 보장 규칙과 그 근거가 되는 약관 조항의 연결.
 *
 * <p>양쪽 끝이 모두 공용 영역이다. "이 보장 규칙의 근거는 약관 몇 조다"는 상품의 사실이지 가입자의 사실이 아니다. 같은 상품을 산 사람 모두에게 같은
 * 조항이 근거가 된다.
 *
 * <p>이전에는 사용자 담보({@code coverage_items})와 약관 청크를 잇고 있었다. 그러면 한 행이 사용자 영역과 공용 영역에 걸쳐, 같은 조항을 가입자
 * 수만큼 복제해야 했다.
 */
@Getter
@Entity
@Table(
    name = "policy_terms_coverage_sources",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_policy_terms_coverage_sources_coverage_chunk_role",
            columnNames = {"terms_coverage_id", "terms_chunk_id", "source_role"}),
    indexes =
        @Index(
            name = "idx_policy_terms_coverage_sources_terms_chunk_id",
            columnList = "terms_chunk_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyTermsCoverageSource extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "terms_coverage_id", nullable = false)
  private PolicyTermsCoverage termsCoverage;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "terms_chunk_id", nullable = false)
  private PolicyTermsChunk termsChunk;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_role", nullable = false, length = 30)
  private TermsCoverageSourceRole sourceRole = TermsCoverageSourceRole.PRIMARY;

  @Column(name = "quote_text", columnDefinition = "TEXT")
  private String quoteText;

  @Builder
  public PolicyTermsCoverageSource(
      PolicyTermsCoverage termsCoverage,
      PolicyTermsChunk termsChunk,
      TermsCoverageSourceRole sourceRole,
      String quoteText) {
    this.termsCoverage = Objects.requireNonNull(termsCoverage, "termsCoverage는 필수입니다.");
    this.termsChunk = Objects.requireNonNull(termsChunk, "termsChunk는 필수입니다.");
    validateSameTerms(termsCoverage, termsChunk);
    this.sourceRole = sourceRole == null ? TermsCoverageSourceRole.PRIMARY : sourceRole;
    this.quoteText = quoteText;
  }

  /**
   * 규칙과 조항은 같은 약관에 속해야 한다.
   *
   * <p>양쪽이 공용 영역으로 모이면서 검사가 이만큼 단순해졌다. 이전에는 사용자 담보 → 분석 → 연결된 약관을 거쳐 확인해야 했고, 그 경로 어딘가가 비어
   * 있으면 판정할 수 없었다.
   *
   * <p>이 검사가 막는 것은 <b>다른 상품의 조항을 근거로 붙이는 일</b>이다. 그렇게 붙으면 화면에는 그럴듯한 조항이 인용되지만 실제로는 그 상품과 무관한
   * 문장이다. 사용자는 그것을 보고 청구를 포기하거나, 되지 않을 청구를 준비한다.
   */
  private void validateSameTerms(PolicyTermsCoverage coverage, PolicyTermsChunk chunk) {
    PolicyTerms coverageTerms = coverage.getTerms();
    PolicyTerms chunkTerms = chunk.getTerms();

    if (coverageTerms == chunkTerms) {
      return;
    }

    UUID coverageTermsId = coverageTerms == null ? null : coverageTerms.getId();
    UUID chunkTermsId = chunkTerms == null ? null : chunkTerms.getId();

    if (coverageTermsId == null || chunkTermsId == null || !coverageTermsId.equals(chunkTermsId)) {
      throw new IllegalArgumentException("근거 조항은 그 보장 규칙과 같은 약관의 조항이어야 합니다.");
    }
  }
}
