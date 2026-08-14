package polight.server.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import polight.server.global.security.JwtTokenProvider;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserService userService;
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private KakaoOAuthClient kakaoOAuthClient;

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
}
