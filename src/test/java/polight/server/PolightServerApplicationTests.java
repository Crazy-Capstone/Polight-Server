package polight.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:testdb",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "oauth.kakao.client-id=test-client-id",
      "oauth.kakao.redirect-uri=http://localhost/test",
      "security.jwt.secret=test-secret-key-with-at-least-32-bytes"
    })
class PolightServerApplicationTests {

  @Test
  void contextLoads() {}
}
