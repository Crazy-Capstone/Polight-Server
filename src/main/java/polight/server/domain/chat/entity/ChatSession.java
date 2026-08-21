package polight.server.domain.chat.entity;

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
import jakarta.persistence.PreUpdate;
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
    name = "chat_sessions",
    indexes = @Index(name = "idx_chat_sessions_user_id", columnList = "user_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatSession extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trip_id")
  private Trip trip;

  @Column(nullable = false, length = 100)
  private String title;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ChatSessionStatus status = ChatSessionStatus.OPEN;

  @Column(name = "started_at", nullable = false)
  private LocalDateTime startedAt;

  @Column(name = "last_active_at", nullable = false)
  private LocalDateTime lastActiveAt;

  @Builder
  public ChatSession(
      User user,
      Trip trip,
      String title,
      ChatSessionStatus status,
      LocalDateTime startedAt,
      LocalDateTime lastActiveAt) {
    this.user = user;
    this.trip = trip;
    this.title = title;
    this.status = status == null ? ChatSessionStatus.OPEN : status;
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
