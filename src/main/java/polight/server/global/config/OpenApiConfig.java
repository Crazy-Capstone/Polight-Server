package polight.server.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private static final String BEARER_SCHEME_NAME = "bearerAuth";

  @Bean
  public OpenAPI openAPI() {
    SecurityScheme bearerScheme =
        new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .description("`POST /api/auth/kakao/login` 응답의 accessToken 값을 그대로 입력합니다.");

    return new OpenAPI()
        .info(
            new Info()
                .title("Polight API")
                .version("v1")
                .description("여행 보험 증권·약관 분석 서비스 API 문서"))
        .components(new Components().addSecuritySchemes(BEARER_SCHEME_NAME, bearerScheme))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME));
  }
}
