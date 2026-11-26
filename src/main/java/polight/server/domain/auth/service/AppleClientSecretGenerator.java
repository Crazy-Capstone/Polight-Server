package polight.server.domain.auth.service;

import io.jsonwebtoken.Jwts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import polight.server.domain.auth.config.AppleOAuthProperties;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@Component
@RequiredArgsConstructor
public class AppleClientSecretGenerator {

  private final AppleOAuthProperties properties;

  public String generate() {
    try {
      if (!StringUtils.hasText(properties.clientId()) || !StringUtils.hasText(properties.teamId())
          || !StringUtils.hasText(properties.keyId()) || !StringUtils.hasText(properties.privateKeyPath())) {
        throw new IllegalArgumentException();
      }
      String pem = Files.readString(Path.of(properties.privateKeyPath()));
      if (!pem.contains("-----BEGIN PRIVATE KEY-----") || !pem.contains("-----END PRIVATE KEY-----")) {
        throw new IllegalArgumentException();
      }
      byte[] der = Base64.getDecoder().decode(pem.replace("-----BEGIN PRIVATE KEY-----", "")
          .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", ""));
      var key = (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
      AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
      parameters.init(new ECGenParameterSpec("secp256r1"));
      ECParameterSpec expected = parameters.getParameterSpec(ECParameterSpec.class);
      if (!key.getParams().getCurve().equals(expected.getCurve())
          || !key.getParams().getGenerator().equals(expected.getGenerator())
          || !key.getParams().getOrder().equals(expected.getOrder())) {
        throw new IllegalArgumentException();
      }
      Instant now = Instant.now();
      return Jwts.builder().header().keyId(properties.keyId()).and()
          .issuer(properties.teamId()).subject(properties.clientId())
          .audience().add(AppleOAuthProperties.ISSUER).and()
          .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(300)))
          .signWith(key, Jwts.SIG.ES256).compact();
    } catch (Exception exception) {
      throw new BaseException(ErrorCode.APPLE_CONFIGURATION_ERROR);
    }
  }
}
