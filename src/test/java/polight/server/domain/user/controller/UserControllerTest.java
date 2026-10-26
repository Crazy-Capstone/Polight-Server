package polight.server.domain.user.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import polight.server.domain.user.repository.UserRepository;
import polight.server.domain.user.service.UserWithdrawalService;
import polight.server.global.security.JwtTokenProvider;
import polight.server.global.security.SecurityConfig;
import polight.server.global.security.RestAuthenticationEntryPoint;
import polight.server.global.security.RestAccessDeniedHandler;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class UserControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private UserWithdrawalService service;
  @MockitoBean private JwtTokenProvider jwtTokenProvider;
  @MockitoBean private UserRepository userRepository;

  @Test
  void withdrawsAuthenticatedUserAndReturns204() throws Exception {
    UUID userId = UUID.randomUUID();
    mvc.perform(delete("/api/users/withdraw").with(authentication(
        new UsernamePasswordAuthenticationToken(userId, null, List.of()))))
        .andExpect(status().isNoContent());
    verify(service).withdraw(userId);
  }

  @Test
  void rejectsUnauthenticatedRequest() throws Exception {
    mvc.perform(delete("/api/users/withdraw")).andExpect(status().isUnauthorized());
    verifyNoInteractions(service);
  }

  @Test
  void rejectsValidTokenBelongingToDeletedUser() throws Exception {
    UUID deletedUser = UUID.randomUUID();
    given(jwtTokenProvider.parseUserId("old-access-token")).willReturn(deletedUser);
    given(userRepository.existsById(deletedUser)).willReturn(false);
    mvc.perform(delete("/api/users/withdraw").header("Authorization", "Bearer old-access-token"))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(service);
  }
}
