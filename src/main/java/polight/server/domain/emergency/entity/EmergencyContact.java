package polight.server.domain.emergency.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import polight.server.domain.policy.entity.Policy;

@Getter
@Entity
@Table(
    name = "emergency_contacts",
    indexes = {
      @Index(name = "idx_emergency_contacts_country_type", columnList = "country_code,type"),
      @Index(name = "idx_emergency_contacts_policy_id", columnList = "policy_id")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmergencyContact extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "country_code", nullable = false, length = 10)
  private String countryCode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private EmergencyContactType type;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(length = 50)
  private String phone;

  @Column(length = 500)
  private String description;

  @Column(name = "insurer_name", length = 200)
  private String insurerName;

  @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
  @JoinColumn(name = "policy_id")
  private Policy policy;

  @Builder
  public EmergencyContact(
      String countryCode,
      EmergencyContactType type,
      String name,
      String phone,
      String description,
      String insurerName,
      Policy policy) {
    this.countryCode = countryCode;
    this.type = type;
    this.name = name;
    this.phone = phone;
    this.description = description;
    this.insurerName = insurerName;
    this.policy = policy;
  }
}
