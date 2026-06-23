package polight.server.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import polight.server.domain.common.entity.BaseTimeEntity;
import polight.server.domain.user.entity.User;

@Getter
@Entity
@Table(name = "notification_preferences", uniqueConstraints = @UniqueConstraint(columnNames = "user_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationPreference extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "policy_expiry_enabled", nullable = false)
  private boolean policyExpiryEnabled = true;

  @Column(name = "renewal_enabled", nullable = false)
  private boolean renewalEnabled = true;

  @Column(name = "analysis_done_enabled", nullable = false)
  private boolean analysisDoneEnabled = true;

  @Column(name = "push_enabled", nullable = false)
  private boolean pushEnabled = true;

  @Builder
  public NotificationPreference(
      User user,
      Boolean policyExpiryEnabled,
      Boolean renewalEnabled,
      Boolean analysisDoneEnabled,
      Boolean pushEnabled) {
    this.user = user;
    this.policyExpiryEnabled = policyExpiryEnabled == null || policyExpiryEnabled;
    this.renewalEnabled = renewalEnabled == null || renewalEnabled;
    this.analysisDoneEnabled = analysisDoneEnabled == null || analysisDoneEnabled;
    this.pushEnabled = pushEnabled == null || pushEnabled;
  }

  public void update(
      Boolean policyExpiryEnabled,
      Boolean renewalEnabled,
      Boolean analysisDoneEnabled,
      Boolean pushEnabled) {
    if (policyExpiryEnabled != null) this.policyExpiryEnabled = policyExpiryEnabled;
    if (renewalEnabled != null) this.renewalEnabled = renewalEnabled;
    if (analysisDoneEnabled != null) this.analysisDoneEnabled = analysisDoneEnabled;
    if (pushEnabled != null) this.pushEnabled = pushEnabled;
  }
}
