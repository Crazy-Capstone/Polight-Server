package polight.server.domain.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import polight.server.domain.user.dto.UserDto;
import polight.server.global.api.ApiResponse;
import polight.server.global.config.OpenApiConfig;
import polight.server.domain.user.service.UserService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
@Tag(name = "User", description = "내 정보 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class UserController {

  private final UserService userService;

  @GetMapping("/me")
  @Operation(summary = "내 정보 조회", description = "JWT principal의 사용자 정보를 조회합니다.")
  public ApiResponse<UserDto.Response> getMe(@AuthenticationPrincipal UUID userId) {
    return ApiResponse.ok(userService.getMe(userId));
  }

  @PatchMapping("/me")
  @Operation(summary = "내 정보 수정", description = "여권번호 원문은 암호화 도입 전까지 입력받지 않습니다.")
  public ApiResponse<UserDto.Response> updateMe(@AuthenticationPrincipal UUID userId,
      @Valid @RequestBody UserDto.UpdateRequest request) {
    return ApiResponse.ok(userService.updateMe(userId, request));
  }
}
