package polight.server.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthTokenResponse(
    @Schema(description = "서비스 API 호출에 사용하는 JWT access token", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,
    @Schema(description = "access token 만료까지 남은 초", example = "3600") Long expiresInSeconds,
    @Schema(description = "사용자 닉네임", example = "홍길동") String nickname,
    @Schema(
            description = "카카오 프로필 이미지 URL. 사용자가 프로필 이미지를 제공하지 않으면 null",
            example = "https://k.kakaocdn.net/dn/abc/img_640x640.jpg")
        String profileImageUrl) {}
