package polight.server.domain.trip.entity;

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
import java.time.LocalDate;
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
    name = "trips",
    indexes = {
      @Index(name = "idx_trips_user_id", columnList = "user_id"),
      @Index(name = "idx_trips_user_status", columnList = "user_id,status"),
      @Index(name = "idx_trips_user_dates", columnList = "user_id,start_date,end_date")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trip extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 100)
  private String title;

  @Column(name = "country_code", nullable = false, length = 10)
  private String countryCode;

  @Column(name = "country_name", nullable = false, length = 100)
  private String countryName;

  @Column(name = "city_name", length = 100)
  private String cityName;

  @Column(name = "flag_emoji", length = 10)
  private String flagEmoji;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TripStatus status = TripStatus.PLANNED;

  @Builder
  public Trip(
      User user,
      String title,
      String countryCode,
      String countryName,
      String cityName,
      String flagEmoji,
      LocalDate startDate,
      LocalDate endDate,
      TripStatus status) {
    this.user = user;
    this.title = title;
    this.countryCode = countryCode;
    this.countryName = countryName;
    this.cityName = cityName;
    this.flagEmoji = flagEmoji;
    this.startDate = startDate;
    this.endDate = endDate;
    this.status = status == null ? TripStatus.PLANNED : status;
  }
}
