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
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.user.entity.User;

@Getter
@Entity
@Table(
    name = "policy_documents",
    indexes = {
      @Index(name = "idx_policy_documents_user_id", columnList = "user_id")
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

  @Column(name = "original_filename", nullable = false, length = 255)
  private String originalFilename;

  @Column(name = "stored_file_path", nullable = false, length = 500)
  private String storedFilePath;

  @Column(name = "content_type", length = 100)
  private String contentType;

  @Column(name = "file_size")
  private Long fileSize;

  /** 증권인지 약관인지. AI 서버에 분석을 요청할 때 documentType 으로 실어 보낸다. */
  @Enumerated(EnumType.STRING)
  @Column(name = "document_kind", nullable = false, length = 20)
  private DocumentKind documentKind = DocumentKind.CERTIFICATE;

  @Enumerated(EnumType.STRING)
  @Column(name = "parse_status", nullable = false, length = 20)
  private DocumentParseStatus parseStatus = DocumentParseStatus.UPLOADED;

  @Column(name = "uploaded_at", nullable = false)
  private LocalDateTime uploadedAt;

  @Builder
  public PolicyDocument(
      User user,
      Trip trip,
      String originalFilename,
      String storedFilePath,
      String contentType,
      Long fileSize,
      DocumentKind documentKind,
      DocumentParseStatus parseStatus,
      LocalDateTime uploadedAt) {
    this.user = user;
    this.trip = trip;
    this.originalFilename = originalFilename;
    this.storedFilePath = storedFilePath;
    this.contentType = contentType;
    this.fileSize = fileSize;
    this.documentKind = documentKind == null ? DocumentKind.CERTIFICATE : documentKind;
    this.parseStatus = parseStatus == null ? DocumentParseStatus.UPLOADED : parseStatus;
    this.uploadedAt = uploadedAt;
  }

  /** 분석이 끝나 이 문서에서 더 뽑을 것이 없는 상태. AI 서버 콜백을 받아 전이한다. */
  public void markParseCompleted() {
    this.parseStatus = DocumentParseStatus.COMPLETED;
  }

  public void markParseFailed() {
    this.parseStatus = DocumentParseStatus.FAILED;
  }

  /**
   * 분석 재시도를 위해 업로드 직후 상태로 되돌린다.
   *
   * <p>{@code PROCESSING}이 아니라 {@code UPLOADED}로 돌리는 이유: 최초 분석이 진행되는 동안에도 이 값은 {@code UPLOADED}에
   * 머문다. 재시도를 {@code PROCESSING}으로 두면 같은 상황의 문서가 최초 시도인지 재시도인지에 따라 다른 값을 갖게 된다.
   */
  public void markParseUploaded() {
    this.parseStatus = DocumentParseStatus.UPLOADED;
  }

  @PrePersist
  void prePersist() {
    if (uploadedAt == null) {
      uploadedAt = LocalDateTime.now();
    }
  }
}
