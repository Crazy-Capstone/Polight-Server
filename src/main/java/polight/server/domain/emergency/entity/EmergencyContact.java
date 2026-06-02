package polight.server.domain.emergency.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "emergency_contacts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmergencyContact {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "country_code", nullable = false, length = 10)
  private String countryCode;

  @Column(name = "country_name", nullable = false, length = 100)
  private String countryName;

  @Column(name = "contact_type", nullable = false, length = 50)
  private String contactType;

  @Column(name = "modal_type", length = 50)
  private String modalType;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(length = 50)
  private String phone;

  @Column(length = 500)
  private String address;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Builder
  public EmergencyContact(
      String countryCode,
      String countryName,
      String contactType,
      String modalType,
      String name,
      String phone,
      String address,
      LocalDateTime updatedAt) {
    this.countryCode = countryCode;
    this.countryName = countryName;
    this.contactType = contactType;
    this.modalType = modalType;
    this.name = name;
    this.phone = phone;
    this.address = address;
    this.updatedAt = updatedAt;
  }

  @PrePersist
  @PreUpdate
  void updateTimestamp() {
    updatedAt = LocalDateTime.now();
  }
}
