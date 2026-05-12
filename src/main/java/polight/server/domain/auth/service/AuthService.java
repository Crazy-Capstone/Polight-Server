package polight.server.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.auth.dto.AuthTokenResponse;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.service.UserService;
import polight.server.global.security.JwtTokenProvider;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserService userService;
  private final JwtTokenProvider jwtTokenProvider;

  @Transactional
  public AuthTokenResponse loginWithKakao(String email, String name) {
    User user = userService.findOrCreateKakaoUser(email, name);
    String accessToken = jwtTokenProvider.createAccessToken(user);

    return new AuthTokenResponse(accessToken, jwtTokenProvider.getAccessTokenExpirySeconds());
  }
}
