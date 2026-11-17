package polight.server.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SignupRequiredResponse(
    @Schema(example = "SIGNUP_REQUIRED") String code,
    String message,
    @Schema(description = "카카오 가입 완료 API에만 사용할 수 있는 일회용 토큰") String signupToken,
    @Schema(description = "가입 토큰 유효 시간(초)", example = "300") long expiresInSeconds) {}
