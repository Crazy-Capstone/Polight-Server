package polight.server.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;

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
