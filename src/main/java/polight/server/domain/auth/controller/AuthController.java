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
          "프론트엔드가 카카오 OAuth 인가 코드(authorizationCode)를 전달하면 서버가 Kakao API로 사용자 정보를 조회하고 서비스 JWT를 발급합니다.")
  public ResponseEntity<AuthTokenResponse> kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
    return ResponseEntity.ok(authService.loginWithKakao(request.authorizationCode()));
  }
}
