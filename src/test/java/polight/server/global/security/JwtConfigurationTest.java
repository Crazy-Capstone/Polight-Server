package polight.server.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;

class JwtConfigurationTest {

  private static final String SECRET = "test-secret-key-with-at-least-32-bytes";

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withUserConfiguration(JwtConfiguration.class)
      .withInitializer(context -> {
        try {
          var sources = new YamlPropertySourceLoader().load(
              "application", new ClassPathResource("application.yaml"));
          sources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
        } catch (IOException exception) {
          throw new IllegalStateException(exception);
        }
      });

  @Test
  void mapsUppercasePropertyFromImportedDotEnvToJwtSecret() {
    runner.withPropertyValues("SECURITY_JWT_SECRET=" + SECRET).run(context -> {
      assertThat(context).hasNotFailed().hasSingleBean(JwtTokenProvider.class);
      assertThat(context.getEnvironment().getProperty("security.jwt.secret")).isEqualTo(SECRET);
    });
  }

  @Test
  void stillAllowsExplicitJwtPropertyOverride() {
    runner.withPropertyValues("security.jwt.secret=" + SECRET).run(context -> {
      assertThat(context).hasNotFailed().hasSingleBean(JwtTokenProvider.class);
    });
  }

  @Test
  void doesNotUseAnInsecureDefaultWhenSecretIsBlank() {
    runner.withPropertyValues("SECURITY_JWT_SECRET=").run(context -> {
      assertThat(context).hasFailed();
      assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
    });
  }

  @Configuration(proxyBeanMethods = false)
  @Import(JwtTokenProvider.class)
  static class JwtConfiguration {}
}
