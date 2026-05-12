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
  public User findOrCreateKakaoUser(String email, String name) {
    return userRepository
        .findByEmail(email)
        .map(
            existingUser -> {
              existingUser.updateName(name);
              return existingUser;
            })
        .orElseGet(
            () ->
                userRepository.save(
                    User.builder().email(email).name(name).provider(User.Provider.KAKAO).build()));
  }
}
