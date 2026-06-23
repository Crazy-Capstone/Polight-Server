package polight.server.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.repository.UserRepository;
import polight.server.domain.user.dto.UserDto;
import polight.server.global.exception.BusinessException;
import polight.server.global.exception.ErrorCode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public UserDto.Response getMe(UUID userId) {
    return toResponse(findUser(userId));
  }

  @Transactional
  public UserDto.Response updateMe(UUID userId, UserDto.UpdateRequest request) {
    User user = findUser(userId);
    user.updatePublicProfile(request.name(), request.phone(), request.avatarEmoji(),
        request.passportName(), request.nationalityCode());
    return toResponse(user);
  }

  @Transactional(readOnly = true)
  public User findUser(UUID userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
  }

  private UserDto.Response toResponse(User user) {
    String passportState = user.getPassportNoEncrypted() == null ? null : "등록됨";
    return new UserDto.Response(user.getId(), user.getEmail(), user.getName(), user.getPhone(),
        user.getAvatarEmoji(), user.getPassportName(), passportState, user.getNationalityCode());
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
