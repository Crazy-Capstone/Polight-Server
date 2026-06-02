package polight.server.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthTokenResponse(
    @Schema(description = "서비스 API 호출에 사용하는 JWT access token", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,
    @Schema(description = "access token 만료까지 남은 초", example = "3600") Long expiresInSeconds) {}
