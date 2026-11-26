package polight.server.domain.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oauth.apple")
public record AppleOAuthProperties(
    String clientId,
    String teamId,
    String keyId,
    String privateKeyPath,
    String tokenEncryptionKey,
    String jwksUri,
    String tokenUri,
    String revokeUri,
    Duration connectTimeout,
    Duration readTimeout) {

  public static final String ISSUER = "https://appleid.apple.com";

  public AppleOAuthProperties {
    clientId = clientId == null ? "com.polight.app" : clientId;
    jwksUri = jwksUri == null ? ISSUER + "/auth/keys" : jwksUri;
    tokenUri = tokenUri == null ? ISSUER + "/auth/token" : tokenUri;
    revokeUri = revokeUri == null ? ISSUER + "/auth/revoke" : revokeUri;
    connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
    readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
  }

  @Override
  public String toString() {
    return "AppleOAuthProperties[redacted]";
  }
}
