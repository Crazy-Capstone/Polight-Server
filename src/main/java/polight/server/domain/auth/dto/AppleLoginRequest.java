package polight.server.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AppleLoginRequest(
    @Schema(description = "Apple이 발급한 Identity Token", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(max = 16384) String identityToken,
    @Schema(description = "Apple 일회용 Authorization Code", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(max = 4096) String authorizationCode,
    @Schema(description = "Apple에 전달한 SHA-256 소문자 hex 해시의 원본 nonce", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(max = 256) String nonce,
    @Schema(description = "최초 Apple 로그인에서 받은 이름. 생략하면 기존 이름 유지 또는 기본 닉네임 사용", nullable = true)
        @Size(max = 100) String name) {

  @Override
  public String toString() {
    return "AppleLoginRequest[redacted]";
  }
}
