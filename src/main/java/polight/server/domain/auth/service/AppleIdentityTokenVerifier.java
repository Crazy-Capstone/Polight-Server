package polight.server.domain.auth.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import polight.server.domain.auth.config.AppleOAuthProperties;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@Component
public class AppleIdentityTokenVerifier {

  private final NimbusJwtDecoder decoder;

  public AppleIdentityTokenVerifier(AppleOAuthProperties properties) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.connectTimeout());
    factory.setReadTimeout(properties.readTimeout());
    decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwksUri())
        .jwsAlgorithm(SignatureAlgorithm.RS256).restOperations(new RestTemplate(factory)).build();
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        new JwtTimestampValidator(Duration.ZERO), new JwtIssuerValidator(AppleOAuthProperties.ISSUER), jwt -> {
          Instant now = Instant.now();
          boolean valid = StringUtils.hasText(jwt.getSubject()) && jwt.getSubject().length() <= 100
              && jwt.getAudience() != null && jwt.getAudience().contains(properties.clientId())
              && jwt.getIssuedAt() != null && !jwt.getIssuedAt().isAfter(now.plusSeconds(30))
              && jwt.getExpiresAt() != null && jwt.getExpiresAt().isAfter(now)
              && jwt.getExpiresAt().isAfter(jwt.getIssuedAt());
          return valid ? OAuth2TokenValidatorResult.success()
              : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        }));
  }

  public String verify(String identityToken, String nonce) {
    try {
      var header = SignedJWT.parse(identityToken).getHeader();
      if (!JWSAlgorithm.RS256.equals(header.getAlgorithm()) || !StringUtils.hasText(header.getKeyID())) {
        throw new BaseException(ErrorCode.APPLE_IDENTITY_TOKEN_INVALID);
      }
      var verified = decoder.decode(identityToken);
      var claim = verified.getClaims().get("nonce");
      if (!(claim instanceof String expected) || !StringUtils.hasText(nonce)
          || !MessageDigest.isEqual(hash(nonce), expected.getBytes(StandardCharsets.US_ASCII))) {
        throw new BaseException(ErrorCode.APPLE_IDENTITY_TOKEN_INVALID);
      }
      return verified.getSubject();
    } catch (BadJwtException | ParseException | IllegalArgumentException exception) {
      throw new BaseException(ErrorCode.APPLE_IDENTITY_TOKEN_INVALID);
    } catch (JwtException exception) {
      throw new BaseException(ErrorCode.APPLE_SERVICE_UNAVAILABLE);
    }
  }

  private byte[] hash(String nonce) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(nonce.getBytes(StandardCharsets.UTF_8))).getBytes(StandardCharsets.US_ASCII);
    } catch (NoSuchAlgorithmException exception) {
      throw new BaseException(ErrorCode.APPLE_CONFIGURATION_ERROR);
    }
  }
}
