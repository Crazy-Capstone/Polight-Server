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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "chat_messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "session_id", nullable = false)
  private ChatSession session;

  @Column(nullable = false, length = 20)
  private String role;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "incident_type", length = 50)
  private String incidentType;

  @Column(name = "sent_at", nullable = false)
  private LocalDateTime sentAt;

  @Builder
  public ChatMessage(ChatSession session, String role, String content, String incidentType, LocalDateTime sentAt) {
    this.session = session;
    this.role = role;
    this.content = content;
    this.incidentType = incidentType;
    this.sentAt = sentAt;
  }

  @PrePersist
  void prePersist() {
    if (sentAt == null) {
      sentAt = LocalDateTime.now();
    }
  }
}
