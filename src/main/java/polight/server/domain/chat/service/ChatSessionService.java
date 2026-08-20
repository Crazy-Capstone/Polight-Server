package polight.server.domain.chat.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.chat.entity.ChatSession;
import polight.server.domain.chat.repository.ChatSessionRepository;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.trip.service.TripService;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

/**
 * 여행의 대화 세션을 확보한다.
 *
 * <p>세션 목록·생성 API를 따로 두지 않는다. 프론트 챗봇 화면은 하단 탭 하나이고 세션을 고르거나 새로 만드는 UI가 없어, 사용자가 여러 세션을 구분해 쓸 방법이
 * 없다. 첫 질문이 들어올 때 서버가 하나 만들고 계속 재사용한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatSessionService {

  private final ChatSessionRepository chatSessionRepository;
  private final TripService tripService;

  /**
   * 여행의 세션을 돌려준다. 없으면 만든다.
   *
   * <p>여행 소유권을 먼저 확인하므로 남의 여행에 세션이 생기지 않는다.
   *
   * <p>동시에 첫 질문이 두 번 들어오면 유니크 인덱스(V7)가 한쪽을 막고 {@code
   * DataIntegrityViolationException}이 올라간다. 이 트랜잭션은 롤백되므로 여기서 잡지 않고, 호출한 쪽이 {@link #getSession}으로
   * 다시 조회한다.
   */
  @Transactional
  public ChatSession getOrCreateSession(UUID userId, UUID tripId) {
    Trip trip = tripService.getOwnedTrip(userId, tripId);

    return chatSessionRepository
        .findByUserIdAndTripId(userId, tripId)
        .orElseGet(
            () ->
                chatSessionRepository.save(
                    ChatSession.builder()
                        .user(trip.getUser())
                        .trip(trip)
                        // 세션 이름을 사용자에게 물을 화면이 없다. 여행 이름을 그대로 쓴다.
                        // trip_name 과 title 이 모두 varchar(100) 이라 잘릴 일은 없다.
                        .title(trip.getName())
                        .build()));
  }

  /** 이미 만들어진 세션을 돌려준다. 생성 경합에서 밀린 요청이 쓴다. */
  public ChatSession getSession(UUID userId, UUID tripId) {
    return chatSessionRepository
        .findByUserIdAndTripId(userId, tripId)
        .orElseThrow(() -> new BaseException(ErrorCode.CHAT_SESSION_NOT_FOUND));
  }
}
