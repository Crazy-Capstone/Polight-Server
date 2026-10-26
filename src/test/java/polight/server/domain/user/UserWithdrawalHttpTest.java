package polight.server.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import polight.server.domain.auth.service.RefreshTokenStore;
import polight.server.domain.chat.entity.ChatMessage;
import polight.server.domain.chat.entity.ChatSender;
import polight.server.domain.chat.entity.ChatSession;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.insurance.storage.PolicyDocumentStorage;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.repository.UserRepository;
import polight.server.global.security.JwtTokenProvider;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:withdrawal-http;MODE=PostgreSQL;NON_KEYWORDS=VALUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "spring.docker.compose.enabled=false",
    "analysis.timeout.enabled=false",
    "storage.type=local",
    "oauth.kakao.client-id=test-client-id",
    "oauth.kakao.redirect-uri=http://localhost/test",
    "security.jwt.secret=test-secret-key-with-at-least-32-bytes"
})
@DirtiesContext
class UserWithdrawalHttpTest {

  @TempDir static Path documentDirectory;

  @Autowired private TestRestTemplate http;
  @Autowired private EntityManager em;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private UserRepository userRepository;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private PolicyDocumentStorage storage;
  @MockitoBean private RefreshTokenStore refreshTokenStore;

  @DynamicPropertySource
  static void configureStorage(DynamicPropertyRegistry registry) {
    registry.add("storage.policy-documents-directory", () -> documentDirectory.toString());
  }

  @Test
  void withdrawalDeletesOwnedDataAndFilesAndRejectsReusedTokenOverHttp() {
    var tx = new TransactionTemplate(transactionManager);
    Fixture owner = tx.execute(status -> createFixture("owner"));
    Fixture other = tx.execute(status -> createFixture("other"));

    assertThat(http.exchange("/api/users/withdraw", HttpMethod.DELETE, HttpEntity.EMPTY, String.class)
        .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(userRepository.existsById(owner.userId())).isTrue();
    assertThat(owner.file()).exists();

    var response = http.exchange("/api/users/withdraw", HttpMethod.DELETE,
        authenticated(owner), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(response.getBody()).isNull();
    assertThat(userRepository.existsById(owner.userId())).isFalse();
    assertThat(owner.file()).doesNotExist();
    verify(refreshTokenStore).revokeAll(owner.userId());
    tx.executeWithoutResult(status -> {
      assertThat(em.find(Trip.class, owner.tripId())).isNull();
      assertThat(em.find(PolicyDocument.class, owner.documentId())).isNull();
      assertThat(em.find(ChatSession.class, owner.sessionId())).isNull();
      assertThat(em.find(ChatMessage.class, owner.messageId())).isNull();
      assertThat(em.find(User.class, other.userId())).isNotNull();
      assertThat(em.find(Trip.class, other.tripId())).isNotNull();
      assertThat(em.find(PolicyDocument.class, other.documentId())).isNotNull();
      assertThat(em.find(ChatSession.class, other.sessionId())).isNotNull();
      assertThat(em.find(ChatMessage.class, other.messageId())).isNotNull();
    });
    assertThat(other.file()).exists();

    assertThat(http.exchange("/api/users/withdraw", HttpMethod.DELETE,
        authenticated(owner), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(other.file()).exists();
  }

  private HttpEntity<Void> authenticated(Fixture fixture) {
    var headers = new HttpHeaders();
    headers.setBearerAuth(fixture.accessToken());
    return new HttpEntity<>(headers);
  }

  private Fixture createFixture(String name) {
    User user = persist(User.builder().name(name).provider(User.Provider.KAKAO)
        .providerId(UUID.randomUUID().toString()).build());
    Trip trip = persist(Trip.builder().user(user).name(name + " trip")
        .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(1)).build());
    String path = storage.store(new MockMultipartFile(
        "file", "test.pdf", "application/pdf", "private-test-content".getBytes()));
    PolicyDocument document = persist(PolicyDocument.builder().user(user).trip(trip)
        .originalFilename("test.pdf").storedFilePath(path).build());
    ChatSession session = persist(ChatSession.builder().user(user).trip(trip).title("chat").build());
    ChatMessage message = persist(ChatMessage.builder().session(session)
        .sender(ChatSender.USER).content("private-test-message").build());
    em.flush();
    return new Fixture(user.getId(), trip.getId(), document.getId(), session.getId(),
        message.getId(), Path.of(path), jwtTokenProvider.createAccessToken(user));
  }

  private <T> T persist(T entity) {
    em.persist(entity);
    return entity;
  }

  private record Fixture(UUID userId, UUID tripId, UUID documentId, UUID sessionId,
      UUID messageId, Path file, String accessToken) {}
}
