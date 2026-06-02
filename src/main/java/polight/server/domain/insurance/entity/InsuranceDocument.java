package polight.server.domain.insurance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.user.entity.User;

@Getter
@Entity
@Table(name = "insurance_documents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InsuranceDocument {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trip_id")
  private Trip trip;

  @Column(name = "insurer_name", length = 200)
  private String insurerName;

  @Column(name = "product_name", length = 200)
  private String productName;

  @Column(name = "file_path", nullable = false, length = 500)
  private String filePath;

  @Column(name = "file_status", nullable = false, length = 20)
  private String fileStatus = "uploaded";

  @Column(name = "coverage_rate")
  private Float coverageRate;

  @Column(name = "uploaded_at", nullable = false)
  private LocalDateTime uploadedAt;

  @Builder
  public InsuranceDocument(
      User user,
      Trip trip,
      String insurerName,
      String productName,
      String filePath,
      String fileStatus,
      Float coverageRate,
      LocalDateTime uploadedAt) {
    this.user = user;
    this.trip = trip;
    this.insurerName = insurerName;
    this.productName = productName;
    this.filePath = filePath;
    this.fileStatus = fileStatus == null ? "uploaded" : fileStatus;
    this.coverageRate = coverageRate;
    this.uploadedAt = uploadedAt;
  }

  @PrePersist
  void prePersist() {
    if (uploadedAt == null) {
      uploadedAt = LocalDateTime.now();
    }
  }
}
