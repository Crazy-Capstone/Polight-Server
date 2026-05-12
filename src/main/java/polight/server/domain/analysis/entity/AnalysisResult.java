package polight.server.domain.analysis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import polight.server.domain.common.entity.BaseTimeEntity;
import polight.server.domain.insurance.entity.InsuranceDocument;

@Getter
@Entity
@Table(name = "analysis_results")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisResult extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "document_id", nullable = false)
  private InsuranceDocument document;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String summary;

  @Builder
  public AnalysisResult(InsuranceDocument document, String summary) {
    this.document = document;
    this.summary = summary;
  }
}
