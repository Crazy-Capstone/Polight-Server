package polight.server.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import polight.server.domain.auth.dto.AuthTokenResponse;
import polight.server.domain.auth.dto.KakaoLoginRequest;
import polight.server.domain.auth.dto.RefreshTokenRequest;
import polight.server.domain.auth.service.AuthService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "인증 API")
public class AuthController {

  private final AuthService authService;

  @PostMapping("/kakao/login")
  @SecurityRequirements
  @Operation(
      summary = "카카오 로그인",
      description =
          "카카오 인가 코드로 사용자를 확인하고, 신규 사용자는 자동 가입한 뒤 서비스 토큰을 발급합니다. "
              + "동의 내역은 로그인 이후 /api/consents에서 별도로 저장합니다.")
  @ApiResponse(responseCode = "200", description = "로그인 또는 자동 가입 성공")
  @ApiResponse(responseCode = "400", description = "INVALID_INPUT")
  @ApiResponse(responseCode = "401", description = "카카오 인증 또는 사용자 조회 실패")
  public ResponseEntity<AuthTokenResponse> kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
    return ResponseEntity.ok(authService.loginWithKakao(request.authorizationCode()));
  }

  @PostMapping("/refresh")
  @Operation(
      summary = "토큰 재발급",
      description =
          "리프레시 토큰으로 새 access token 을 발급합니다. 리프레시 토큰도 새 값으로 바뀌므로(회전),"
              + " 응답에 실려 온 refreshToken 으로 저장해 둔 값을 반드시 덮어써야 합니다."
              + " 이미 쓰였거나 만료된 토큰이면 401(INVALID_REFRESH_TOKEN)이며, 이때는 다시 로그인해야 합니다."
              + " 응답의 profileImageUrl 은 항상 null 입니다 -- 카카오에서만 오는 값이라 서버가 보관하지 않습니다.")
  public ResponseEntity<AuthTokenResponse> refresh(
      @Valid @RequestBody RefreshTokenRequest request) {
    return ResponseEntity.ok(authService.refresh(request.refreshToken()));
  }

  @PostMapping("/logout")
  @Operation(
      summary = "로그아웃",
      description =
          "리프레시 토큰을 무효화합니다. 이미 무효한 토큰을 보내도 204 입니다 -- 목적이 이미 달성된 상태이기 때문입니다."
              + " 이미 발급된 access token 은 만료 전까지 계속 유효하므로, 클라이언트도 저장해 둔 토큰을 함께 지워야 합니다.")
  public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
    authService.logout(request.refreshToken());
    return ResponseEntity.noContent().build();
  }
}
