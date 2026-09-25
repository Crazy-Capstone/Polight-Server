package polight.server;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import polight.server.domain.terms.service.AiTermsMatcher;
import polight.server.domain.terms.service.TermsMatcher;

/**
 * {@code terms.matching.provider=ai} 로 켰을 때 기동되는지 본다.
 *
 * <p>구현을 조건부 빈으로 고르므로, 조건이 어긋나면 {@code TermsMatcher} 빈이 없거나 둘이 되어 <b>기동 시점에</b> 터진다.
 * 그때는 배포가 통째로 막히는데, 평소 테스트는 기본값(local)으로만 돌아 드러나지 않는다.
 */
@SpringBootTest(
    properties = {
      "terms.matching.provider=ai",
      "spring.datasource.url=jdbc:h2:mem:aitermsdb",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "oauth.kakao.client-id=test-client-id",
      "oauth.kakao.redirect-uri=http://localhost/test",
      "security.jwt.secret=test-secret-key-with-at-least-32-bytes"
    })
class AiTermsMatchingContextTest {

  @Autowired private TermsMatcher termsMatcher;

  @Test
  void aiProviderPicksTheAiMatcher() {
    assertThat(termsMatcher).isInstanceOf(AiTermsMatcher.class);
  }
}
