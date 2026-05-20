package polight.server.domain.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oauth.kakao")
public record KakaoOAuthProperties(String clientId, String redirectUri, String clientSecret) {}
