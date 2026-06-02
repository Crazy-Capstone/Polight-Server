package polight.server.domain.trip.entity;

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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import polight.server.domain.user.entity.User;

@Getter
@Entity
@Table(name = "trips")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trip {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 100)
  private String country;

  @Column(length = 100)
  private String city;

  @Column(name = "country_emoji", length = 10)
  private String countryEmoji;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @Column(nullable = false, length = 20)
  private String status = "upcoming";

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Builder
  public Trip(
      User user,
      String country,
      String city,
      String countryEmoji,
      LocalDate startDate,
      LocalDate endDate,
      String status,
      LocalDateTime createdAt) {
    this.user = user;
    this.country = country;
    this.city = city;
    this.countryEmoji = countryEmoji;
    this.startDate = startDate;
    this.endDate = endDate;
    this.status = status == null ? "upcoming" : status;
    this.createdAt = createdAt;
  }

  @PrePersist
  void prePersist() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
  }
}
