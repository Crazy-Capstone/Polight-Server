package polight.server.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 재발급과 로그아웃이 같은 요청 형태를 쓴다. 둘 다 "이 리프레시 토큰에 대해" 무언가를 하라는 요청이고, 실을 것이 토큰 하나뿐이라 형태를 나눌 이유가 없다.
 */
public record RefreshTokenRequest(
    @Schema(
            description = "로그인 시 발급받은 리프레시 토큰",
            example = "9qL3kT7xR2mVb8nZ5wQ1yF6cH0dJ4sA-eG7uP2iO3rE")
        @NotBlank(message = "refreshToken은 필수입니다.")
        String refreshToken) {}
