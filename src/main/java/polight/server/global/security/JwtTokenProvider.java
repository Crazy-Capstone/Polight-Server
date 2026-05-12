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

  public JwtTokenProvider(
      @Value("${security.jwt.secret}") String secret,
      @Value("${security.jwt.access-token-expiry-seconds:3600}") long accessTokenExpirySeconds) {
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessTokenExpirySeconds = accessTokenExpirySeconds;
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
