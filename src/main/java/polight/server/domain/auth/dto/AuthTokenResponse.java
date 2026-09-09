package polight.server.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthTokenResponse(
    @Schema(description = "서비스 API 호출에 사용하는 JWT access token", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,
    @Schema(
            description =
                "access token 이 만료됐을 때 POST /api/auth/refresh 로 새 토큰을 받는 데 쓴다."
                    + " 재발급 때마다 새 값으로 바뀌므로 응답에 실려 온 값으로 매번 덮어써야 한다.",
            example = "9qL3kT7xR2mVb8nZ5wQ1yF6cH0dJ4sA-eG7uP2iO3rE")
        String refreshToken,
    @Schema(description = "access token 만료까지 남은 초", example = "3600") Long expiresInSeconds,
    @Schema(description = "사용자 닉네임", example = "홍길동") String nickname,
    @Schema(
            description =
                "카카오 프로필 이미지 URL. 사용자가 프로필 이미지를 제공하지 않으면 null."
                    + " 카카오에서 받아 그대로 전달하는 값이라 로그인 응답에만 담기고, 재발급 응답에서는 항상 null 이다.",
            example = "https://k.kakaocdn.net/dn/abc/img_640x640.jpg")
        String profileImageUrl) {}
