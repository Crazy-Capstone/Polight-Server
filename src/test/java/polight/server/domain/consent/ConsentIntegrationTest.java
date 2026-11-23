package polight.server.domain.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import jakarta.persistence.EntityManager;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import polight.server.domain.consent.dto.ConsentCreateRequest;
import polight.server.domain.consent.dto.ConsentResponse;
import polight.server.domain.consent.dto.ConsentUpdateRequest;
import polight.server.domain.consent.entity.Consent;
import polight.server.domain.consent.entity.ConsentHistory;
import polight.server.domain.consent.entity.ConsentType;
import polight.server.domain.consent.repository.ConsentHistoryRepository;
import polight.server.domain.consent.repository.ConsentRepository;
import polight.server.domain.consent.service.ConsentService;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.repository.UserRepository;
import polight.server.domain.user.repository.UserWithdrawalRepository;
import polight.server.global.config.JpaAuditingConfig;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@DataJpaTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=${POLIGHT_CONSENT_TEST_DB_URL:jdbc:h2:mem:consent;MODE=PostgreSQL;NON_KEYWORDS=VALUE}",
    "spring.datasource.driver-class-name=${POLIGHT_CONSENT_TEST_DB_DRIVER:org.h2.Driver}",
    "spring.datasource.username=${POLIGHT_CONSENT_TEST_DB_USER:sa}",
    "spring.datasource.password=",
    "spring.flyway.enabled=${POLIGHT_CONSENT_TEST_FLYWAY:false}",
    "spring.jpa.hibernate.ddl-auto=${POLIGHT_CONSENT_TEST_DDL:create-drop}"
}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ConsentService.class, UserWithdrawalRepository.class, JpaAuditingConfig.class})
class ConsentIntegrationTest {

  @Autowired private ConsentService service;
  @Autowired private ConsentRepository repository;
  @Autowired private ConsentHistoryRepository histories;
  @Autowired private UserRepository users;
  @Autowired private UserWithdrawalRepository withdrawal;
  @Autowired private EntityManager em;
  @Autowired private PlatformTransactionManager transactions;

  @Test
  void storesFalseAndTrueForAllSevenTypesAndReadsTheSameSnapshot() {
    User user = user();
    var values = values(false);
    values.put(ConsentType.TERMS_OF_SERVICE, true);
    var created = service.create(user.getId(), new ConsentCreateRequest(values));
    em.clear();

    assertThat(created.id()).isNotNull();
    assertThat(created.consents()).isEqualTo(values);
    assertThat(created.createdAt()).isNotNull();
    assertThat(created.updatedAt()).isNotNull();
    assertThat(service.get(user.getId())).isEqualTo(created);
    assertThat(repository.findByUserId(user.getId())).isPresent();
    assertThat(histories.findByUserIdOrderByIdDesc(user.getId())).isEmpty();
  }

  @Test
  void duplicatePostDoesNotChangeExistingValues() {
    User user = user();
    var original = service.create(user.getId(), new ConsentCreateRequest(values(false)));

    assertThatExceptionOfType(BaseException.class)
        .isThrownBy(() -> service.create(user.getId(), new ConsentCreateRequest(values(true))))
        .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CONSENT_ALREADY_EXISTS));
    assertThat(service.get(user.getId())).isEqualTo(original);
  }

  @Test
  void patchPreservesOmittedValuesIdentityAndCreatedAt() {
    User user = user();
    var original = service.create(user.getId(), new ConsentCreateRequest(values(true)));
    em.clear();
    var changed = service.update(user.getId(),
        new ConsentUpdateRequest(Map.of(ConsentType.THIRD_PARTY_POLICY, false)));
    em.clear();

    assertThat(changed.id()).isEqualTo(original.id());
    assertThat(changed.createdAt()).isEqualTo(original.createdAt());
    assertThat(changed.updatedAt()).isAfter(original.updatedAt());
    var expected = values(true);
    expected.put(ConsentType.THIRD_PARTY_POLICY, false);
    assertThat(changed.consents()).isEqualTo(expected);
    assertThat(service.get(user.getId())).isEqualTo(changed);
  }

  @Test
  void legacyHistoryRemainsUntouchedAndIsNotImplicitlyConverted() {
    User user = user();
    ConsentHistory history = histories.saveAndFlush(
        new ConsentHistory(user, ConsentType.HEALTH_INFORMATION, "legacy", true));
    assertThatExceptionOfType(BaseException.class).isThrownBy(() -> service.get(user.getId()))
        .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CONSENT_NOT_FOUND));
    service.create(user.getId(), new ConsentCreateRequest(values(false)));
    assertThat(histories.findById(history.getId())).isPresent();
    assertThat(histories.findByUserIdOrderByIdDesc(user.getId())).hasSize(1);
  }

  @Test
  void userDeletionCascadesCurrentAndLegacyConsentButKeepsOtherUsersData() {
    User target = user();
    User other = user();
    service.create(target.getId(), new ConsentCreateRequest(values(false)));
    service.create(other.getId(), new ConsentCreateRequest(values(true)));
    histories.saveAndFlush(new ConsentHistory(target, ConsentType.TERMS_OF_SERVICE, "legacy", true));

    withdrawal.deleteUserData(target.getId());
    em.clear();

    assertThat(repository.findByUserId(target.getId())).isEmpty();
    assertThat(histories.findByUserIdOrderByIdDesc(target.getId())).isEmpty();
    assertThat(service.get(other.getId()).consents()).isEqualTo(values(true));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentPostsCreateOneRowAndReturnOneConflict() throws Exception {
    UUID userId = user().getId();
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var futures = List.of(false, true).stream().map(agreed -> executor.submit(() -> {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        try {
          service.create(userId, new ConsentCreateRequest(values(agreed)));
          return "CREATED";
        } catch (BaseException error) {
          return error.getErrorCode().name();
        }
      })).toList();
      start.countDown();
      assertThat(List.of(futures.get(0).get(10, TimeUnit.SECONDS),
          futures.get(1).get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("CREATED", "CONSENT_ALREADY_EXISTS");
      assertThat(repository.findByUserId(userId)).isPresent();
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentPatchesToDifferentFieldsDoNotLoseEitherChange() throws Exception {
    UUID userId = user().getId();
    service.create(userId, new ConsentCreateRequest(values(false)));
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var futures = List.of(ConsentType.THIRD_PARTY_POLICY, ConsentType.HEALTH_INFORMATION)
          .stream().map(type -> executor.submit(() -> {
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            return service.update(userId, new ConsentUpdateRequest(Map.of(type, true)));
          })).toList();
      start.countDown();
      for (var future : futures) {
        future.get(10, TimeUnit.SECONDS);
      }
      var expected = values(false);
      expected.put(ConsentType.THIRD_PARTY_POLICY, true);
      expected.put(ConsentType.HEALTH_INFORMATION, true);
      assertThat(service.get(userId).consents()).isEqualTo(expected);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void outerTransactionRollbackUndoesPatch() {
    UUID userId = user().getId();
    ConsentResponse original = service.create(userId, new ConsentCreateRequest(values(false)));
    new TransactionTemplate(transactions).executeWithoutResult(status -> {
      service.update(userId, new ConsentUpdateRequest(Map.of(ConsentType.HEALTH_INFORMATION, true)));
      status.setRollbackOnly();
    });
    assertThat(service.get(userId)).isEqualTo(original);
  }

  @Test
  void databaseUniqueConstraintPreventsBypassingPostValidation() {
    User user = user();
    repository.saveAndFlush(new Consent(user, values(false)));
    assertThatExceptionOfType(org.springframework.dao.DataIntegrityViolationException.class)
        .isThrownBy(() -> repository.saveAndFlush(new Consent(user, values(true))));
  }

  private User user() {
    return users.saveAndFlush(User.builder().provider(User.Provider.KAKAO)
        .providerId(UUID.randomUUID().toString()).name("Tester").build());
  }

  private EnumMap<ConsentType, Boolean> values(boolean agreed) {
    var values = new EnumMap<ConsentType, Boolean>(ConsentType.class);
    for (var type : ConsentType.values()) {
      values.put(type, agreed);
    }
    return values;
  }
}
