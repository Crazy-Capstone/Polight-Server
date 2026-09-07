package polight.server.domain.auth.service;

import java.util.UUID;
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
  private final RefreshTokenStore refreshTokenStore;

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

    // 카카오가 함께 내려주는 refresh_token 은 쓰지 않고 버린다. 우리가 카카오 API 를 다시 호출할
    // 일이 로그인 시점 말고는 없어서, 보관하면 지킬 것만 늘고 얻는 것이 없다. 카카오 프로필을
    // 나중에 다시 읽어야 하는 기능이 생기면 그때 이 값을 저장소에 함께 넣는다.
    User user = userService.findOrCreateKakaoUser(providerId, email, nickname == null ? "카카오사용자" : nickname);

    return issueTokens(user, profileImageUrl);
  }

  /**
   * 리프레시 토큰으로 access token 을 다시 발급한다.
   *
   * <p>리프레시 토큰도 함께 새 값으로 바뀐다(회전). 한 번 쓴 토큰을 계속 쓸 수 있게 두면, 유출된 토큰이 만료일까지 살아 있어 그 기간 내내 남의
   * access token 을 찍어낼 수 있다. 매번 갈아 끼우면 유출된 토큰의 수명이 "정상 사용자가 다음 재발급을 할 때까지"로 줄고, 둘 중 늦은 쪽은
   * 재발급에 실패해 다시 로그인하게 되므로 이상이 드러난다.
   *
   * <p>DB 트랜잭션을 열지 않는다. 토큰 소비는 Redis 에서 이미 원자적으로 끝나 있고, 사용자 조회는 {@link UserService}가 자기
   * 트랜잭션으로 처리한다. 여기서 트랜잭션을 감싸면 Redis 왕복이 커넥션을 쥔 채로 진행된다.
   */
  public AuthTokenResponse refresh(String refreshToken) {
    UUID userId = refreshTokenStore.consume(refreshToken);

    // 탈퇴한 사용자의 토큰이 남아 있을 수 있다. 이미 위에서 소비해 지웠으므로 다시 쓰이지 않는다.
    User user = userService.getUser(userId);

    // profileImageUrl 은 카카오에서만 오는 값이라 여기서 채울 수 없다. 우리 DB 에 두지 않기
    // 때문이다. 클라이언트는 로그인 응답에서 받은 값을 그대로 들고 있으면 된다.
    return issueTokens(user, null);
  }

  /**
   * 로그아웃. 리프레시 토큰만 무효화한다.
   *
   * <p>이미 발급된 access token 은 서명이 유효한 동안(최대 {@code access-token-expiry-seconds}) 계속 통한다. 그것을
   * 막으려면 요청마다 저장소를 확인해야 하고, 그러면 access token 이 무상태가 아니게 된다. 만료가 1시간이라 그 대가를 치를 이유가 없다고 보고
   * 두었다 -- 즉시 차단이 필요해지면 그때 블랙리스트를 붙인다.
   */
  public void logout(String refreshToken) {
    refreshTokenStore.revoke(refreshToken);
  }

  private AuthTokenResponse issueTokens(User user, String profileImageUrl) {
    String accessToken = jwtTokenProvider.createAccessToken(user);
    String refreshToken = refreshTokenStore.issue(user.getId());

    return new AuthTokenResponse(
        accessToken,
        refreshToken,
        jwtTokenProvider.getAccessTokenExpirySeconds(),
        user.getName(),
        profileImageUrl);
  }
}
