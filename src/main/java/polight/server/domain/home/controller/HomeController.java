package polight.server.domain.home.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import polight.server.domain.home.dto.HomeSummaryResponse;
import polight.server.domain.home.service.HomeService;
import polight.server.global.api.ApiResponse;
import polight.server.global.config.OpenApiConfig;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/home")
@Tag(name = "Home", description = "홈 화면 조합 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class HomeController {
  private final HomeService homeService;

  @GetMapping("/summary")
  @Operation(summary = "홈 요약 조회", description = "현재 여행·보험·대표 보장·읽지 않은 알림을 한 번에 반환합니다.")
  public ApiResponse<HomeSummaryResponse> summary(@AuthenticationPrincipal UUID userId) {
    return ApiResponse.ok(homeService.summary(userId));
  }
}
