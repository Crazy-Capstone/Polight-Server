package polight.server.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
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
  @Operation(
      summary = "카카오 로그인",
      description =
          "프론트엔드가 카카오 OAuth 인가 코드(authorizationCode)를 전달하면 서버가 Kakao API로 사용자 정보를 조회하고"
              + " 서비스 JWT access token 과 리프레시 토큰을 발급합니다.")
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
