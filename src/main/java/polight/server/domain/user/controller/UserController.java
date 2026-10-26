package polight.server.domain.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import polight.server.domain.user.service.UserWithdrawalService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

  private final UserWithdrawalService userWithdrawalService;

  @DeleteMapping("/withdraw")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "회원 탈퇴", description = "회원과 연관 데이터를 삭제합니다. 원본 파일 삭제 실패 시 로그를 남깁니다.")
  public void withdraw(@AuthenticationPrincipal UUID userId) {
    userWithdrawalService.withdraw(userId);
  }
}
