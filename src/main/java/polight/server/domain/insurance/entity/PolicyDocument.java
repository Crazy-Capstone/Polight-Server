package polight.server.domain.insurance.entity;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import polight.server.domain.common.entity.BaseTimeEntity;
import polight.server.domain.policy.entity.Policy;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.user.entity.User;

@Getter
@Entity
@Table(
    name = "policy_documents",
    indexes = {
      @Index(name = "idx_policy_documents_user_id", columnList = "user_id"),
      @Index(name = "idx_policy_documents_trip_id", columnList = "trip_id"),
      @Index(name = "idx_policy_documents_policy_id", columnList = "policy_id"),
      @Index(name = "idx_policy_documents_parse_status", columnList = "parse_status")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyDocument extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trip_id")
  private Trip trip;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "policy_id")
  private Policy policy;

  @Column(name = "original_filename", nullable = false, length = 255)
  private String originalFilename;

  @Column(name = "stored_file_path", nullable = false, length = 500)
  private String storedFilePath;

  @Column(name = "content_type", length = 100)
  private String contentType;

  @Column(name = "file_size")
  private Long fileSize;

  @Enumerated(EnumType.STRING)
  @Column(name = "parse_status", nullable = false, length = 20)
  private DocumentParseStatus parseStatus = DocumentParseStatus.UPLOADED;

  @Column(name = "uploaded_at", nullable = false)
  private LocalDateTime uploadedAt;

  @Builder
  public PolicyDocument(
      User user,
      Trip trip,
      Policy policy,
      String originalFilename,
      String storedFilePath,
      String contentType,
      Long fileSize,
      DocumentParseStatus parseStatus,
      LocalDateTime uploadedAt) {
    this.user = user;
    this.trip = trip;
    this.policy = policy;
    this.originalFilename = originalFilename;
    this.storedFilePath = storedFilePath;
    this.contentType = contentType;
    this.fileSize = fileSize;
    this.parseStatus = parseStatus == null ? DocumentParseStatus.UPLOADED : parseStatus;
    this.uploadedAt = uploadedAt;
  }

  @PrePersist
  void prePersist() {
    if (uploadedAt == null) {
      uploadedAt = LocalDateTime.now();
    }
  }

  public void markProcessing() {
    this.parseStatus = DocumentParseStatus.PROCESSING;
  }
}
