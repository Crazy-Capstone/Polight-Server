package polight.server.domain.auth.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import polight.server.domain.auth.dto.RefreshTokenRequest;
import polight.server.domain.auth.dto.TokenResponse;
import polight.server.domain.auth.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/refresh")
  public ResponseEntity<TokenResponse> refresh(@RequestBody @Valid RefreshTokenRequest request) {
    return ResponseEntity.ok(authService.refresh(request.refreshToken()));
  }
}
