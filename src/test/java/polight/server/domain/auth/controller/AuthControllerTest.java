package polight.server.domain.auth.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import polight.server.domain.auth.dto.AuthTokenResponse;
import polight.server.domain.auth.service.AuthService;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;
import polight.server.global.security.JwtTokenProvider;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private AuthService authService;

  @MockBean private JwtTokenProvider jwtTokenProvider;

  @Test
  void kakaoLogin_acceptsAuthorizationCodeOnly() throws Exception {
    given(authService.loginWithKakao(anyString()))
        .willReturn(
            new AuthTokenResponse(
                "token", "refresh", 3600L, "닉네임", "https://img.kakao/profile.jpg"));

    mockMvc
        .perform(
            post("/api/auth/kakao/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"authorizationCode":"code"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nickname").value("닉네임"))
        .andExpect(jsonPath("$.refreshToken").value("refresh"))
        .andExpect(jsonPath("$.profileImageUrl").value("https://img.kakao/profile.jpg"));
  }

  @Test
  void kakaoLogin_failsWhenAuthorizationCodeMissing() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/kakao/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void refresh_returnsNewTokenPair() throws Exception {
    given(authService.refresh("old-refresh"))
        .willReturn(new AuthTokenResponse("new-access", "new-refresh", 3600L, "닉네임", null));

    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"refreshToken":"old-refresh"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("new-access"))
        .andExpect(jsonPath("$.refreshToken").value("new-refresh"));
  }

  @Test
  void refresh_failsWhenRefreshTokenMissing() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void refresh_returns401WhenTokenIsAlreadyUsedOrExpired() throws Exception {
    willThrow(new BaseException(ErrorCode.INVALID_REFRESH_TOKEN))
        .given(authService)
        .refresh("dead");

    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"refreshToken":"dead"}
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
  }

  @Test
  void logout_returns204() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"refreshToken":"some-refresh"}
                    """))
        .andExpect(status().isNoContent());

    verify(authService, times(1)).logout("some-refresh");
  }
}
