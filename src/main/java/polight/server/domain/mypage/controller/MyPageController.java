package polight.server.domain.mypage.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import polight.server.domain.mypage.dto.MyPageSummaryResponse;
import polight.server.domain.mypage.service.MyPageService;
import polight.server.global.api.ApiResponse;
import polight.server.global.config.OpenApiConfig;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/mypage")
@Tag(name = "My Page", description = "마이페이지 조합 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class MyPageController {
  private final MyPageService myPageService;

  @GetMapping("/summary")
  @Operation(summary = "마이페이지 요약 조회")
  public ApiResponse<MyPageSummaryResponse> summary(@AuthenticationPrincipal UUID userId) {
    return ApiResponse.ok(myPageService.summary(userId));
  }
}
