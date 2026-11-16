package polight.server.domain.consent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import polight.server.domain.user.entity.User;

@Getter
@Entity
@Immutable
@Table(name = "consent_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsentHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(name = "consent_type", nullable = false, length = 50)
  private ConsentType consentType;

  @Column(name = "document_version", nullable = false, length = 30)
  private String documentVersion;

  @Column(nullable = false)
  private boolean agreed;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public ConsentHistory(User user, ConsentType consentType, String documentVersion, boolean agreed) {
    this.user = user;
    this.consentType = consentType;
    this.documentVersion = documentVersion;
    this.agreed = agreed;
  }
}
