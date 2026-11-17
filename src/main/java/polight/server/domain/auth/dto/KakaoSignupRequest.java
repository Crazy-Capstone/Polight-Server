package polight.server.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import polight.server.domain.consent.dto.ConsentDecision;

public record KakaoSignupRequest(
    @Schema(description = "카카오 로그인에서 SIGNUP_REQUIRED와 함께 반환된 일회용 토큰")
    @NotBlank String signupToken,
    @Schema(description = "회원가입 필수 항목 3개를 포함한 동의 목록")
    List<@Valid @NotNull ConsentDecision> decisions) {}
