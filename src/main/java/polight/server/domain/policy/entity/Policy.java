package polight.server.domain.policy.entity;

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
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import polight.server.domain.common.entity.BaseTimeEntity;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.user.entity.User;

@Getter
@Entity
@Table(
    name = "policies",
    indexes = {
      @Index(name = "idx_policies_user_id", columnList = "user_id"),
      @Index(name = "idx_policies_trip_id", columnList = "trip_id"),
      @Index(name = "idx_policies_user_status", columnList = "user_id,status"),
      @Index(name = "idx_policies_user_dates", columnList = "user_id,start_date,end_date")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Policy extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "trip_id", nullable = false)
  private Trip trip;

  @Column(name = "insurer_name", nullable = false, length = 200)
  private String insurerName;

  @Column(name = "product_name", nullable = false, length = 200)
  private String productName;

  @Column(name = "policy_number_encrypted", length = 500)
  private String policyNumberEncrypted;

  @Column(name = "display_name", nullable = false, length = 200)
  private String displayName;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PolicyStatus status = PolicyStatus.PENDING;

  @Column(name = "coverage_score")
  private Integer coverageScore;

  @Column(name = "coverage_count", nullable = false)
  private int coverageCount;

  @Builder
  public Policy(
      User user,
      Trip trip,
      String insurerName,
      String productName,
      String policyNumberEncrypted,
      String displayName,
      LocalDate startDate,
      LocalDate endDate,
      PolicyStatus status,
      Integer coverageScore,
      Integer coverageCount) {
    this.user = user;
    this.trip = trip;
    this.insurerName = insurerName;
    this.productName = productName;
    this.policyNumberEncrypted = policyNumberEncrypted;
    this.displayName = displayName;
    this.startDate = startDate;
    this.endDate = endDate;
    this.status = status == null ? PolicyStatus.PENDING : status;
    this.coverageScore = coverageScore;
    this.coverageCount = coverageCount == null ? 0 : coverageCount;
  }
}
