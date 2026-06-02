package polight.server.domain.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import polight.server.domain.insurance.entity.InsuranceDocument;
import polight.server.domain.user.entity.User;

@Getter
@Entity
@Table(name = "chat_sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatSession {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "document_id")
  private InsuranceDocument document;

  @Column(name = "started_at", nullable = false)
  private LocalDateTime startedAt;

  @Column(name = "last_active_at", nullable = false)
  private LocalDateTime lastActiveAt;

  @Builder
  public ChatSession(User user, InsuranceDocument document, LocalDateTime startedAt, LocalDateTime lastActiveAt) {
    this.user = user;
    this.document = document;
    this.startedAt = startedAt;
    this.lastActiveAt = lastActiveAt;
  }

  @PrePersist
  void prePersist() {
    LocalDateTime now = LocalDateTime.now();
    if (startedAt == null) {
      startedAt = now;
    }
    if (lastActiveAt == null) {
      lastActiveAt = now;
    }
  }

  @PreUpdate
  void preUpdate() {
    lastActiveAt = LocalDateTime.now();
  }
}
