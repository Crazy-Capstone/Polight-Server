package polight.server.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class UserDto {
  private UserDto() {}

  @Schema(name = "UserResponse", description = "사용자 기본 정보")
  public record Response(
      @Schema(example = "7c9fd445-f43b-477a-a3eb-5ff0d7fbfbd6") UUID id,
      @Schema(example = "user@example.com") String email,
      @Schema(example = "류지") String name,
      @Schema(example = "010-1234-5678") String phone,
      @Schema(example = "🐰") String avatarEmoji,
      @Schema(example = "RYU JI") String passportName,
      @Schema(description = "암호문을 노출하지 않는 등록 상태 표시", example = "등록됨", nullable = true)
          String passportNoMasked,
      @Schema(example = "KR") String nationalityCode) {}

  @Schema(name = "UpdateUserRequest", description = "여권번호 자체는 암호화 도입 전까지 받지 않습니다.")
  public record UpdateRequest(
      @Size(max = 100) @Schema(example = "류지") String name,
      @Size(max = 30) @Schema(example = "010-1234-5678") String phone,
      @Size(max = 10) @Schema(example = "🐰") String avatarEmoji,
      @Size(max = 100) @Schema(example = "RYU JI") String passportName,
      @Pattern(regexp = "^[A-Za-z]{2}$", message = "ISO 2자리 국가 코드여야 합니다.")
          @Schema(example = "KR") String nationalityCode) {}
}
