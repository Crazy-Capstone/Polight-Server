package polight.server.domain.auth.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.consent.dto.ConsentDecision;
import polight.server.domain.consent.service.ConsentService;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.service.UserService;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class KakaoSignupService {

  private final UserService users;
  private final ConsentService consents;

  @Transactional
  public User register(KakaoSignupProfile profile, List<ConsentDecision> decisions) {
    if (users.kakaoUserExists(profile.providerId())) {
      throw new BaseException(ErrorCode.KAKAO_USER_ALREADY_REGISTERED);
    }
    User user = users.createKakaoUser(profile.providerId(), profile.email(), profile.nickname());
    consents.recordConsents(user, decisions);
    return user;
  }
}
