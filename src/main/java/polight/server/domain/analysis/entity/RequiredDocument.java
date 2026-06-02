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

@Getter
@Entity
@Table(name = "required_documents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequiredDocument {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "coverage_item_id", nullable = false)
  private CoverageItem coverageItem;

  @Column(name = "document_name", nullable = false, length = 200)
  private String documentName;

  @Column(name = "is_mandatory", nullable = false)
  private boolean mandatory;

  @Builder
  public RequiredDocument(CoverageItem coverageItem, String documentName, boolean mandatory) {
    this.coverageItem = coverageItem;
    this.documentName = documentName;
    this.mandatory = mandatory;
  }
}
