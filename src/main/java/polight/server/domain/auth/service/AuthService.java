package polight.server.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.auth.client.KakaoOAuthClient;
import polight.server.domain.auth.dto.AuthTokenResponse;
import polight.server.domain.auth.dto.kakao.KakaoUserInfoResponse;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.service.UserService;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;
import polight.server.global.security.JwtTokenProvider;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserService userService;
  private final JwtTokenProvider jwtTokenProvider;
  private final KakaoOAuthClient kakaoOAuthClient;

  @Transactional
  public AuthTokenResponse loginWithKakao(String authorizationCode) {
    var tokenResponse = kakaoOAuthClient.requestToken(authorizationCode);
    KakaoUserInfoResponse userInfo = kakaoOAuthClient.requestUserInfo(tokenResponse.accessToken());

    if (userInfo.id() == null) {
      throw new BaseException(ErrorCode.KAKAO_USER_ID_NOT_FOUND);
    }

    String providerId = String.valueOf(userInfo.id());
    String email = userInfo.kakaoAccount() != null ? userInfo.kakaoAccount().email() : null;
    var profile =
        userInfo.kakaoAccount() != null ? userInfo.kakaoAccount().profile() : null;
    String nickname = profile != null ? profile.nickname() : null;
    String profileImageUrl = profile != null ? profile.profileImageUrl() : null;

    User user = userService.findOrCreateKakaoUser(providerId, email, nickname == null ? "카카오사용자" : nickname);
    String accessToken = jwtTokenProvider.createAccessToken(user);

    return new AuthTokenResponse(
        accessToken, jwtTokenProvider.getAccessTokenExpirySeconds(), user.getName(), profileImageUrl);
  }
}
