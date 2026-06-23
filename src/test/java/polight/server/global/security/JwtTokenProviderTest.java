package polight.server.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import polight.server.domain.user.entity.User;

class JwtTokenProviderTest {

  private static final String SECRET = "12345678901234567890123456789012";

  @Test
  void createAccessToken_canBeParsedBackToUserId() {
    JwtTokenProvider tokenProvider = new JwtTokenProvider(SECRET, 3600L);
    UUID userId = UUID.randomUUID();
    User user =
        User.builder()
            .email("user@example.com")
            .name("tester")
            .provider(User.Provider.KAKAO)
            .providerId("12345")
            .build();
    ReflectionTestUtils.setField(user, "id", userId);

    String accessToken = tokenProvider.createAccessToken(user);

    assertThat(tokenProvider.parseUserId(accessToken)).isEqualTo(userId);
  }

  @Test
  void parseUserId_rejectsInvalidToken() {
    JwtTokenProvider tokenProvider = new JwtTokenProvider(SECRET, 3600L);

    assertThatThrownBy(() -> tokenProvider.parseUserId("invalid-token")).isInstanceOf(Exception.class);
  }
}
