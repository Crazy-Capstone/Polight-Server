package polight.server.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import polight.server.domain.auth.client.KakaoOAuthClient;
import polight.server.domain.auth.dto.AuthTokenResponse;
import polight.server.domain.auth.dto.kakao.KakaoTokenResponse;
import polight.server.domain.auth.dto.kakao.KakaoUserInfoResponse;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.service.UserService;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;
import polight.server.global.security.JwtTokenProvider;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserService userService;
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private KakaoOAuthClient kakaoOAuthClient;
  @Mock private RefreshTokenStore refreshTokenStore;

  @InjectMocks private AuthService authService;

  @Test
  void loginWithKakao_usesProviderIdForFindOrCreate() {
    KakaoUserInfoResponse userInfo =
        new KakaoUserInfoResponse(
            12345L,
            new KakaoUserInfoResponse.KakaoAccount(
                "a@b.com",
                new KakaoUserInfoResponse.Profile("nick", "https://img.kakao/profile.jpg")));
    User user = User.builder().provider(User.Provider.KAKAO).providerId("12345").name("nick").email("a@b.com").build();

    given(kakaoOAuthClient.requestToken("code"))
        .willReturn(new KakaoTokenResponse("bearer", "kakao-access", null, 3600L, null));
    given(kakaoOAuthClient.requestUserInfo("kakao-access")).willReturn(userInfo);
    given(userService.findOrCreateKakaoUser("12345", "a@b.com", "nick")).willReturn(user);
    given(jwtTokenProvider.createAccessToken(any(User.class))).willReturn("service-token");
    given(jwtTokenProvider.getAccessTokenExpirySeconds()).willReturn(3600L);

    authService.loginWithKakao("code");

    verify(userService, times(1)).findOrCreateKakaoUser(eq("12345"), eq("a@b.com"), eq("nick"));
  }

  @Test
  void loginWithKakao_returnsNicknameAndProfileImageUrl() {
    KakaoUserInfoResponse userInfo =
        new KakaoUserInfoResponse(
            12345L,
            new KakaoUserInfoResponse.KakaoAccount(
                "a@b.com",
                new KakaoUserInfoResponse.Profile("nick", "https://img.kakao/profile.jpg")));
    User user =
        User.builder()
            .provider(User.Provider.KAKAO)
            .providerId("12345")
            .name("nick")
            .email("a@b.com")
            .build();

    given(kakaoOAuthClient.requestToken("code"))
        .willReturn(new KakaoTokenResponse("bearer", "kakao-access", null, 3600L, null));
    given(kakaoOAuthClient.requestUserInfo("kakao-access")).willReturn(userInfo);
    given(userService.findOrCreateKakaoUser("12345", "a@b.com", "nick")).willReturn(user);
    given(jwtTokenProvider.createAccessToken(any(User.class))).willReturn("service-token");
    given(jwtTokenProvider.getAccessTokenExpirySeconds()).willReturn(3600L);

    AuthTokenResponse response = authService.loginWithKakao("code");

    assertThat(response.nickname()).isEqualTo("nick");
    assertThat(response.profileImageUrl()).isEqualTo("https://img.kakao/profile.jpg");
  }

  @Test
  void loginWithKakao_returnsNullProfileImageUrlWhenKakaoOmitsProfile() {
    KakaoUserInfoResponse userInfo =
        new KakaoUserInfoResponse(12345L, new KakaoUserInfoResponse.KakaoAccount("a@b.com", null));
    User user =
        User.builder()
            .provider(User.Provider.KAKAO)
            .providerId("12345")
            .name("카카오사용자")
            .email("a@b.com")
            .build();

    given(kakaoOAuthClient.requestToken("code"))
        .willReturn(new KakaoTokenResponse("bearer", "kakao-access", null, 3600L, null));
    given(kakaoOAuthClient.requestUserInfo("kakao-access")).willReturn(userInfo);
    given(userService.findOrCreateKakaoUser("12345", "a@b.com", "카카오사용자")).willReturn(user);
    given(jwtTokenProvider.createAccessToken(any(User.class))).willReturn("service-token");
    given(jwtTokenProvider.getAccessTokenExpirySeconds()).willReturn(3600L);

    AuthTokenResponse response = authService.loginWithKakao("code");

    assertThat(response.nickname()).isEqualTo("카카오사용자");
    assertThat(response.profileImageUrl()).isNull();
  }

  @Test
  void loginWithKakao_issuesRefreshTokenForTheUser() {
    KakaoUserInfoResponse userInfo =
        new KakaoUserInfoResponse(
            12345L,
            new KakaoUserInfoResponse.KakaoAccount(
                "a@b.com", new KakaoUserInfoResponse.Profile("nick", null)));
    User user =
        User.builder()
            .provider(User.Provider.KAKAO)
            .providerId("12345")
            .name("nick")
            .email("a@b.com")
            .build();

    given(kakaoOAuthClient.requestToken("code"))
        .willReturn(new KakaoTokenResponse("bearer", "kakao-access", "kakao-refresh", 3600L, null));
    given(kakaoOAuthClient.requestUserInfo("kakao-access")).willReturn(userInfo);
    given(userService.findOrCreateKakaoUser("12345", "a@b.com", "nick")).willReturn(user);
    given(jwtTokenProvider.createAccessToken(any(User.class))).willReturn("service-token");
    given(jwtTokenProvider.getAccessTokenExpirySeconds()).willReturn(3600L);
    given(refreshTokenStore.issue(user.getId())).willReturn("refresh-token");

    AuthTokenResponse response = authService.loginWithKakao("code");

    assertThat(response.accessToken()).isEqualTo("service-token");
    assertThat(response.refreshToken()).isEqualTo("refresh-token");
  }

  @Test
  void refresh_consumesOldTokenAndIssuesNewPair() {
    UUID userId = UUID.randomUUID();
    User user =
        User.builder()
            .provider(User.Provider.KAKAO)
            .providerId("12345")
            .name("nick")
            .email("a@b.com")
            .build();

    given(refreshTokenStore.consume("old-refresh")).willReturn(userId);
    given(userService.getUser(userId)).willReturn(user);
    given(jwtTokenProvider.createAccessToken(user)).willReturn("new-access");
    given(jwtTokenProvider.getAccessTokenExpirySeconds()).willReturn(3600L);
    given(refreshTokenStore.issue(user.getId())).willReturn("new-refresh");

    AuthTokenResponse response = authService.refresh("old-refresh");

    assertThat(response.accessToken()).isEqualTo("new-access");
    // 회전: 응답에 실린 리프레시 토큰은 요청에 실려 온 것과 달라야 한다. 같으면 한 번 유출된
    // 토큰이 만료일까지 계속 통한다는 뜻이다.
    assertThat(response.refreshToken()).isEqualTo("new-refresh").isNotEqualTo("old-refresh");
    verify(refreshTokenStore, times(1)).consume("old-refresh");
  }

  @Test
  void refresh_returnsNullProfileImageUrlBecauseServerDoesNotKeepIt() {
    UUID userId = UUID.randomUUID();
    User user =
        User.builder()
            .provider(User.Provider.KAKAO)
            .providerId("12345")
            .name("nick")
            .email("a@b.com")
            .build();

    given(refreshTokenStore.consume("old-refresh")).willReturn(userId);
    given(userService.getUser(userId)).willReturn(user);
    given(jwtTokenProvider.createAccessToken(user)).willReturn("new-access");
    given(jwtTokenProvider.getAccessTokenExpirySeconds()).willReturn(3600L);

    AuthTokenResponse response = authService.refresh("old-refresh");

    assertThat(response.profileImageUrl()).isNull();
    assertThat(response.nickname()).isEqualTo("nick");
  }

  @Test
  void refresh_propagatesInvalidRefreshTokenAndIssuesNothing() {
    given(refreshTokenStore.consume("dead"))
        .willThrow(new BaseException(ErrorCode.INVALID_REFRESH_TOKEN));

    assertThatThrownBy(() -> authService.refresh("dead"))
        .isInstanceOf(BaseException.class)
        .extracting(e -> ((BaseException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

    verify(refreshTokenStore, never()).issue(any());
    verifyNoInteractions(jwtTokenProvider);
  }

  @Test
  void logout_revokesTheRefreshToken() {
    authService.logout("some-refresh");

    verify(refreshTokenStore, times(1)).revoke("some-refresh");
  }
}
