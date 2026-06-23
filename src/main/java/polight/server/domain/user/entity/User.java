package polight.server.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import polight.server.domain.common.entity.BaseTimeEntity;

@Getter
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

  public enum Provider {
    KAKAO
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(length = 100)
  private String email;

  @Column(name = "password_hash")
  private String passwordHash;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 30)
  private String phone;

  @Column(name = "avatar_emoji", length = 10)
  private String avatarEmoji;

  @Column(name = "passport_name", length = 100)
  private String passportName;

  @Column(name = "passport_no_encrypted", length = 500)
  private String passportNoEncrypted;

  @Column(name = "nationality_code", length = 10)
  private String nationalityCode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private Provider provider;

  @Column(name = "provider_id", nullable = false, length = 100)
  private String providerId;

  @Builder
  public User(
      String email,
      String passwordHash,
      String name,
      String phone,
      String avatarEmoji,
      String passportName,
      String passportNoEncrypted,
      String nationalityCode,
      Provider provider,
      String providerId) {
    this.email = email;
    this.passwordHash = passwordHash;
    this.name = name;
    this.phone = phone;
    this.avatarEmoji = avatarEmoji;
    this.passportName = passportName;
    this.passportNoEncrypted = passportNoEncrypted;
    this.nationalityCode = nationalityCode;
    this.provider = provider;
    this.providerId = providerId;
  }

  public void updateProfile(String email, String name) {
    this.email = email;
    this.name = name;
  }

  public void updateProfile(
      String name,
      String phone,
      String passportName,
      String passportNoEncrypted,
      String nationalityCode,
      String avatarEmoji) {
    this.name = name;
    this.phone = phone;
    this.passportName = passportName;
    this.passportNoEncrypted = passportNoEncrypted;
    this.nationalityCode = nationalityCode;
    this.avatarEmoji = avatarEmoji;
  }
}
