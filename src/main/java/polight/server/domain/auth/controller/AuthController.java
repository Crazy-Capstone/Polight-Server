package polight.server.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import polight.server.domain.auth.dto.AuthTokenResponse;
import polight.server.domain.auth.dto.KakaoLoginRequest;
import polight.server.domain.auth.dto.KakaoSignupRequest;
import polight.server.domain.auth.dto.RefreshTokenRequest;
import polight.server.domain.auth.dto.SignupRequiredResponse;
import polight.server.domain.auth.exception.SignupRequiredException;
import polight.server.domain.auth.service.AuthService;
import polight.server.global.exception.ErrorResponse;

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
          "카카오 인가 코드로 사용자를 확인합니다. 기존 회원은 서비스 토큰을 받습니다. "
              + "신규 사용자는 409 응답의 signupToken으로 가입을 완료해야 합니다.")
  @ApiResponse(responseCode = "200", description = "기존 회원 로그인 성공")
  @ApiResponse(responseCode = "400", description = "INVALID_INPUT")
  @ApiResponse(responseCode = "401", description = "카카오 인증 또는 사용자 조회 실패")
  @ApiResponse(responseCode = "409", description = "SIGNUP_REQUIRED: 가입 완료 요청 필요",
      content = @Content(schema = @Schema(implementation = SignupRequiredResponse.class)))
  public ResponseEntity<AuthTokenResponse> kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
    return ResponseEntity.ok(authService.loginWithKakao(request.authorizationCode()));
  }

  @PostMapping("/kakao/signup")
  @SecurityRequirements
  @Operation(summary = "카카오 가입 완료",
      description = "일회용 signupToken과 동의를 제출합니다. 필수 동의 검증 후 계정과 이력을 저장하고 토큰을 발급합니다.")
  @ApiResponse(responseCode = "200", description = "가입 완료 및 서비스 토큰 발급")
  @ApiResponse(responseCode = "400", description = "INVALID_INPUT 또는 REQUIRED_CONSENT_NOT_AGREED",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(responseCode = "401", description = "INVALID_SIGNUP_TOKEN: 만료, 사용 완료 또는 잘못된 토큰",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(responseCode = "409", description = "KAKAO_USER_ALREADY_REGISTERED",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public ResponseEntity<AuthTokenResponse> kakaoSignup(@Valid @RequestBody KakaoSignupRequest request) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(authService.signupWithKakao(request.signupToken(), request.decisions()));
  }

  @ExceptionHandler(SignupRequiredException.class)
  public ResponseEntity<SignupRequiredResponse> signupRequired(SignupRequiredException exception) {
    var code = exception.getErrorCode();
    return ResponseEntity.status(code.getStatus()).cacheControl(CacheControl.noStore())
        .body(new SignupRequiredResponse(
            code.name(), code.getMessage(), exception.getSignupToken(), exception.getExpiresInSeconds()));
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
