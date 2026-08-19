package polight.server.domain.trip.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import polight.server.domain.common.entity.BaseTimeEntity;
import polight.server.domain.concern.entity.Concern;
import polight.server.domain.user.entity.User;

@Getter
@Entity
@Table(name = "trips")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trip extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "trip_name", nullable = false, length = 100)
  private String name;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TripStatus status = TripStatus.PLANNED;

  /**
   * 사용자가 고른 걱정되는 상황. 보장 내역을 보여줄 때 선택한 항목에 해당하는 담보를 위로 올리는 데 쓴다.
   *
   * <p>선택은 여행의 속성이다. 같은 여행에 증권을 다시 올렸다고 걱정이 바뀌지는 않는다. 저장할 때 중복을 없애고 enum 선언 순서로 맞춘다.
   */
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "concerns", nullable = false, columnDefinition = "varchar(50)[]")
  private List<Concern> concerns = new ArrayList<>();

  @Builder
  public Trip(
      User user,
      String name,
      LocalDate startDate,
      LocalDate endDate,
      TripStatus status,
      Collection<Concern> concerns) {
    this.user = user;
    this.name = name;
    this.startDate = startDate;
    this.endDate = endDate;
    this.status = status == null ? TripStatus.PLANNED : status;
    this.concerns = normalizeConcerns(concerns);
  }

  public void update(String name, LocalDate startDate, LocalDate endDate) {
    this.name = name;
    this.startDate = startDate;
    this.endDate = endDate;
  }

  /** 중복을 제거하고 enum 선언 순서로 정렬한다. 같은 선택이 어떤 순서로 와도 저장된 값이 같아진다. */
  private static List<Concern> normalizeConcerns(Collection<Concern> concerns) {
    if (concerns == null || concerns.isEmpty()) {
      return new ArrayList<>();
    }
    return new LinkedHashSet<>(concerns).stream().sorted().collect(Collectors.toCollection(ArrayList::new));
  }
}
