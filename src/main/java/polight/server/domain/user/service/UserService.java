package polight.server.domain.user.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.repository.UserRepository;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;

  /**
   * 사용자 엔티티를 돌려준다. 없으면 {@link ErrorCode#USER_NOT_FOUND}.
   *
   * <p>다른 도메인 서비스가 사용자 엔티티를 필요로 할 때 UserRepository를 직접 쓰지 않고 이 메서드를 거친다.
   */
  @Transactional(readOnly = true)
  public User getUser(UUID userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
  }

  @Transactional
  public User findOrCreateKakaoUser(String providerId, String email, String name) {
    return userRepository
        .findByProviderAndProviderId(User.Provider.KAKAO, providerId)
        .map(
            existingUser -> {
              existingUser.updateProfile(email, name);
              return existingUser;
            })
        .orElseGet(
            () ->
                userRepository.save(
                    User.builder()
                        .provider(User.Provider.KAKAO)
                        .providerId(providerId)
                        .email(email)
                        .name(name)
                        .build()));
  }
}
