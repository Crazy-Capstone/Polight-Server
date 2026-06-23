package polight.server.domain.notification.entity;

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
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import polight.server.domain.common.entity.BaseTimeEntity;
import polight.server.domain.user.entity.User;

@Getter
@Entity
@Table(
    name = "notifications",
    indexes = {
      @Index(name = "idx_notifications_user_read", columnList = "user_id,read_at"),
      @Index(name = "idx_notifications_user_created", columnList = "user_id,created_at")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private NotificationType type;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 500)
  private String body;

  @Column(name = "deep_link", length = 500)
  private String deepLink;

  @Column(name = "read_at")
  private LocalDateTime readAt;

  @Builder
  public Notification(
      User user,
      NotificationType type,
      String title,
      String body,
      String deepLink,
      LocalDateTime readAt) {
    this.user = user;
    this.type = type;
    this.title = title;
    this.body = body;
    this.deepLink = deepLink;
    this.readAt = readAt;
  }

  public void markAsRead(LocalDateTime readAt) {
    this.readAt = readAt == null ? LocalDateTime.now() : readAt;
  }
}
