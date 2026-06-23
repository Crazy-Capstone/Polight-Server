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
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import polight.server.domain.common.entity.BaseTimeEntity;

@Getter
@Entity
@Table(
    name = "chat_messages",
    indexes = @Index(name = "idx_chat_messages_session_created", columnList = "session_id,created_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "session_id", nullable = false)
  private ChatSession session;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ChatSender sender;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(name = "response_type", nullable = false, length = 30)
  private ChatResponseType responseType = ChatResponseType.TEXT;

  @Column(name = "metadata_json", columnDefinition = "TEXT")
  private String metadataJson;

  @Builder
  public ChatMessage(
      ChatSession session,
      ChatSender sender,
      String content,
      ChatResponseType responseType,
      String metadataJson) {
    this.session = session;
    this.sender = sender;
    this.content = content;
    this.responseType = responseType == null ? ChatResponseType.TEXT : responseType;
    this.metadataJson = metadataJson;
  }
}
