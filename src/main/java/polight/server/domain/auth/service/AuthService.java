package polight.server.domain.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.auth.dto.TokenResponse;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.repository.UserRepository;
import polight.server.global.security.jwt.JwtProperties;
import polight.server.global.security.jwt.JwtTokenProvider;

@Service
public class AuthService {

  private final JwtTokenProvider jwtTokenProvider;
  private final JwtProperties jwtProperties;
  private final UserRepository userRepository;

  public AuthService(JwtTokenProvider jwtTokenProvider, JwtProperties jwtProperties,
      UserRepository userRepository) {
    this.jwtTokenProvider = jwtTokenProvider;
    this.jwtProperties = jwtProperties;
    this.userRepository = userRepository;
  }

  public TokenResponse issueTokens(User user) {
    return new TokenResponse(
        jwtTokenProvider.generateAccessToken(user),
        jwtTokenProvider.generateRefreshToken(user),
        "Bearer",
        jwtProperties.accessTokenExpirationSeconds()
    );
  }

  @Transactional(readOnly = true)
  public TokenResponse refresh(String refreshToken) {
    if (!"refresh".equals(jwtTokenProvider.getTokenType(refreshToken))) {
      throw new IllegalArgumentException("Invalid token type");
    }
    User user = userRepository.findById(jwtTokenProvider.getUserId(refreshToken))
        .orElseThrow(() -> new IllegalArgumentException("User not found"));
    return issueTokens(user);
  }
}
