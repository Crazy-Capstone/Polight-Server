package polight.server.domain.auth.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import polight.server.global.security.JwtTokenProvider;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private AuthService authService;

  @MockBean private JwtTokenProvider jwtTokenProvider;

  @Test
  void kakaoLogin_acceptsAuthorizationCodeOnly() throws Exception {
    given(authService.loginWithKakao(anyString())).willReturn(new AuthTokenResponse("token", 3600L));

    mockMvc
        .perform(
            post("/api/auth/kakao/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"authorizationCode":"code"}
                    """))
        .andExpect(status().isOk());
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
}
