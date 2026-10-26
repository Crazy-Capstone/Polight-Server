package polight.server.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import polight.server.domain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

  @Mock private JwtTokenProvider tokenProvider;
  @Mock private UserRepository userRepository;
  @Mock private FilterChain chain;
  @InjectMocks private JwtAuthenticationFilter filter;

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void deletedUserCannotAuthenticateEvenWithValidToken() throws Exception {
    UUID id = UUID.randomUUID();
    given(tokenProvider.parseUserId("valid")).willReturn(id);
    given(userRepository.existsById(id)).willReturn(false);
    MockHttpServletRequest request = request();
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, chain);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain).doFilter(request, response);
  }

  @Test
  void existingUserCanAuthenticate() throws Exception {
    UUID id = UUID.randomUUID();
    given(tokenProvider.parseUserId("valid")).willReturn(id);
    given(userRepository.existsById(id)).willReturn(true);
    filter.doFilter(request(), new MockHttpServletResponse(), chain);
    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(id);
  }

  private MockHttpServletRequest request() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer valid");
    return request;
  }
}
