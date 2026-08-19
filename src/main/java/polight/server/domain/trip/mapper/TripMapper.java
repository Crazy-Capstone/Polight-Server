package polight.server.domain.trip.mapper;

import java.util.List;
import org.springframework.stereotype.Component;
import polight.server.domain.trip.dto.TripCreateRequest;
import polight.server.domain.trip.dto.TripResponse;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.user.entity.User;

/**
 * Trip 엔티티와 DTO 사이의 변환을 담당한다.
 *
 * <p>변환 로직을 DTO의 정적 팩토리에 두면 DTO가 엔티티를 알아야 하므로, 별도 클래스로 분리해 의존 방향을 한쪽으로 정리한다.
 *
 * <p>엔티티를 응답으로 바꾸는 시점은 서비스의 트랜잭션 안이어야 한다. open-in-view가 꺼져 있어 컨트롤러에서 지연 로딩 필드에 접근하면 예외가 난다.
 */
@Component
public class TripMapper {

  public Trip toEntity(User user, TripCreateRequest request) {
    return Trip.builder()
        .user(user)
        .name(request.name().trim())
        .startDate(request.startDate())
        .endDate(request.endDate())
        .concerns(request.concernsOrEmpty())
        .build();
  }

  public TripResponse toResponse(Trip trip) {
    return new TripResponse(
        trip.getId(),
        trip.getName(),
        trip.getStartDate(),
        trip.getEndDate(),
        trip.getStatus(),
        trip.getConcerns(),
        trip.getCreatedAt(),
        trip.getUpdatedAt());
  }

  public List<TripResponse> toResponses(List<Trip> trips) {
    return trips.stream().map(this::toResponse).toList();
  }
}
