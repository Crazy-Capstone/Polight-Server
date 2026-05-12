package polight.server.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;
import polight.server.domain.user.entity.User;

@Component
public class JwtTokenProvider {

  private final JwtProperties jwtProperties;
  private final SecretKey secretKey;

  public JwtTokenProvider(JwtProperties jwtProperties) {
    this.jwtProperties = jwtProperties;
    byte[] keyBytes = jwtProperties.secret().length() >= 32
        ? jwtProperties.secret().getBytes(StandardCharsets.UTF_8)
        : Decoders.BASE64.decode(jwtProperties.secret());
    this.secretKey = Keys.hmacShaKeyFor(keyBytes);
  }

  public String generateAccessToken(User user) {
    return generateToken(user, jwtProperties.accessTokenExpirationSeconds(), "access");
  }

  public String generateRefreshToken(User user) {
    return generateToken(user, jwtProperties.refreshTokenExpirationSeconds(), "refresh");
  }

  public Claims parseClaims(String token) {
    return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
  }

  public UUID getUserId(String token) {
    return UUID.fromString(parseClaims(token).getSubject());
  }

  public String getTokenType(String token) {
    return parseClaims(token).get("type", String.class);
  }

  private String generateToken(User user, long expireSeconds, String type) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(user.getId().toString())
        .claim("email", user.getEmail())
        .claim("role", user.getRole().name())
        .claim("type", type)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(expireSeconds)))
        .signWith(secretKey)
        .compact();
  }
}
