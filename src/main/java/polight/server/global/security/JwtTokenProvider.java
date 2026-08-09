package polight.server.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import polight.server.domain.user.entity.User;

@Component
public class JwtTokenProvider {

  private final SecretKey secretKey;
  private final long accessTokenExpirySeconds;

  private static final int MINIMUM_SECRET_BYTES = 32;

  public JwtTokenProvider(
      @Value("${security.jwt.secret}") String secret,
      @Value("${security.jwt.access-token-expiry-seconds:3600}") long accessTokenExpirySeconds) {
    byte[] secretBytes = validateSecret(secret);
    this.secretKey = Keys.hmacShaKeyFor(secretBytes);
    this.accessTokenExpirySeconds = accessTokenExpirySeconds;
  }

  private static byte[] validateSecret(String secret) {
    if (secret == null || secret.isBlank() || secret.startsWith("${")) {
      throw new IllegalStateException(
          "security.jwt.secret 이 설정되지 않았습니다. SECURITY_JWT_SECRET 환경변수를 주입하세요."
              + " (생성: openssl rand -base64 48)");
    }

    byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    if (secretBytes.length < MINIMUM_SECRET_BYTES) {
      throw new IllegalStateException(
          "security.jwt.secret 은 최소 "
              + MINIMUM_SECRET_BYTES
              + "바이트여야 합니다. 현재 "
              + secretBytes.length
              + "바이트입니다.");
    }

    return secretBytes;
  }

  public String createAccessToken(User user) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(user.getId().toString())
        .claim("email", user.getEmail())
        .claim("name", user.getName())
        .claim("provider", user.getProvider().name())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(Duration.ofSeconds(accessTokenExpirySeconds))))
        .signWith(secretKey)
        .compact();
  }

  public long getAccessTokenExpirySeconds() {
    return accessTokenExpirySeconds;
  }

  public UUID parseUserId(String token) {
    Claims claims =
        Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
    return UUID.fromString(claims.getSubject());
  }
}
