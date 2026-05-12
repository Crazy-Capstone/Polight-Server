package polight.server.domain.insurance.entity;

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
import polight.server.domain.user.entity.User;

@Getter
@Entity
@Table(name = "insurance_documents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InsuranceDocument extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(nullable = false, length = 500)
  private String fileUrl;

  @Builder
  public InsuranceDocument(User user, String title, String fileUrl) {
    this.user = user;
    this.title = title;
    this.fileUrl = fileUrl;
  }
}
