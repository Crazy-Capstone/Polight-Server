package polight.server.domain.auth.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class KakaoLoginTestPageController {

  @GetMapping("/auth/login/kakao")
  public String kakaoLoginCallbackPage() {
    return "forward:/kakao-login-test.html";
  }
}
