package polight.server.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(
    @Schema(description = "카카오 OAuth 로그인 성공 후 redirect URI로 전달되는 인가 코드", example = "abc123-kakao-code")
    @NotBlank(message = "인가 코드는 필수입니다.")
    String authorizationCode) {}
