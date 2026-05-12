package polight.server.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(@NotBlank String email, @NotBlank String name) {}
