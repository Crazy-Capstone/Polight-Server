package polight.server.domain.auth.dto;

public record AuthTokenResponse(String accessToken, Long expiresInSeconds) {}
