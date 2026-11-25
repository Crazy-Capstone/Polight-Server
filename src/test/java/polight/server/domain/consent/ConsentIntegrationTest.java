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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import polight.server.domain.consent.dto.ConsentCreateRequest;
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
  void partialPostStoresOnlyCurrentStateAndReturnsAllSevenValues() {
    UUID userId = user().getId();
    Map<ConsentType, Boolean> collected = Map.of(
        ConsentType.TERMS_OF_SERVICE, true, ConsentType.PRIVACY_COLLECTION_USE, true,
        ConsentType.OVERSEAS_TRANSFER, true, ConsentType.AGE_14_PLUS, false);
    var saved = service.save(userId, new ConsentCreateRequest(collected));
    em.clear();

    assertThat(saved.created()).isTrue();
    assertThat(saved.response().createdAt()).isNotNull();
    assertThat(saved.response().updatedAt()).isNotNull();
    assertThat(service.get(userId)).isEqualTo(saved.response());
    assertThat(saved.response().consents()).hasSize(7).containsAllEntriesOf(collected)
        .containsEntry(ConsentType.POLICY_ANALYSIS, null)
        .containsEntry(ConsentType.THIRD_PARTY_POLICY, null)
        .containsEntry(ConsentType.HEALTH_INFORMATION, null);
    assertThat(repository.findByUserId(userId).orElseThrow().getHealthInformation()).isNull();
    assertThat(histories.findByUserIdOrderByIdDesc(userId)).isEmpty();
  }

  @Test
  void multipleScreenPostsMergeIntoOneCurrentRowAndKeepOnlyLastUpdateTime() {
    UUID userId = user().getId();
    var initial = service.save(userId,
        new ConsentCreateRequest(Map.of(ConsentType.TERMS_OF_SERVICE, true))).response();
    em.clear();
    var second = service.save(userId,
        new ConsentCreateRequest(Map.of(ConsentType.HEALTH_INFORMATION, false)));
    em.clear();

    assertThat(second.created()).isFalse();
    assertThat(second.response().id()).isEqualTo(initial.id());
    assertThat(second.response().createdAt()).isEqualTo(initial.createdAt());
    assertThat(second.response().updatedAt()).isAfter(initial.updatedAt());
    assertThat(second.response().consents()).containsEntry(ConsentType.TERMS_OF_SERVICE, true)
        .containsEntry(ConsentType.HEALTH_INFORMATION, false)
        .containsEntry(ConsentType.POLICY_ANALYSIS, null);
    assertThat(service.get(userId)).isEqualTo(second.response());
    assertThat(histories.findByUserIdOrderByIdDesc(userId)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void collectsNullAsEitherChoiceWithoutAccumulatingHistory(boolean agreed) {
    UUID userId = user().getId();
    service.save(userId, new ConsentCreateRequest(Map.of(ConsentType.TERMS_OF_SERVICE, false)));
    var updated = service.update(userId,
        new ConsentUpdateRequest(Map.of(ConsentType.HEALTH_INFORMATION, agreed)));
    em.clear();

    assertThat(service.get(userId)).isEqualTo(updated);
    assertThat(updated.consents()).containsEntry(ConsentType.HEALTH_INFORMATION, agreed)
        .containsEntry(ConsentType.TERMS_OF_SERVICE, false);
    assertThat(histories.findByUserIdOrderByIdDesc(userId)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"true,false", "false,true"})
  void patchReplacesChoiceAndUpdatesOnlyOverallModifiedTime(boolean before, boolean after) {
    UUID userId = user().getId();
    var initial = service.save(userId,
        new ConsentCreateRequest(Map.of(ConsentType.HEALTH_INFORMATION, before))).response();
    em.clear();
    var updated = service.update(userId,
        new ConsentUpdateRequest(Map.of(ConsentType.HEALTH_INFORMATION, after)));
    em.clear();

    assertThat(updated.consents()).containsEntry(ConsentType.HEALTH_INFORMATION, after);
    assertThat(updated.id()).isEqualTo(initial.id());
    assertThat(updated.createdAt()).isEqualTo(initial.createdAt());
    assertThat(updated.updatedAt()).isAfter(initial.updatedAt());
    assertThat(service.get(userId)).isEqualTo(updated);
    assertThat(histories.findByUserIdOrderByIdDesc(userId)).isEmpty();
  }

  @Test
  void identicalPostAndPatchKeepValuesAndLastActualUpdateTime() {
    UUID userId = user().getId();
    var values = Map.of(ConsentType.HEALTH_INFORMATION, true);
    var initial = service.save(userId, new ConsentCreateRequest(values)).response();
    em.clear();

    var repeated = service.save(userId, new ConsentCreateRequest(values));
    assertThat(repeated.created()).isFalse();
    assertThat(repeated.response()).isEqualTo(initial);
    em.clear();
    assertThat(service.update(userId, new ConsentUpdateRequest(values))).isEqualTo(initial);
    assertThat(histories.findByUserIdOrderByIdDesc(userId)).isEmpty();
  }

  @Test
  void existingHistoryIsNotChangedOrExtendedByCurrentStateApis() {
    User user = user();
    ConsentHistory legacy = histories.saveAndFlush(
        new ConsentHistory(user, ConsentType.HEALTH_INFORMATION, "legacy", true));
    service.save(user.getId(), new ConsentCreateRequest(values(false)));
    service.save(user.getId(), new ConsentCreateRequest(Map.of(ConsentType.HEALTH_INFORMATION, true)));
    service.update(user.getId(), new ConsentUpdateRequest(Map.of(ConsentType.HEALTH_INFORMATION, false)));
    service.get(user.getId());
    em.clear();

    assertThat(histories.findByUserIdOrderByIdDesc(user.getId())).singleElement()
        .satisfies(history -> {
          assertThat(history.getId()).isEqualTo(legacy.getId());
          assertThat(history.isAgreed()).isTrue();
          assertThat(history.getDocumentVersion()).isEqualTo("legacy");
          assertThat(history.getCreatedAt()).isEqualTo(legacy.getCreatedAt());
        });
  }

  @Test
  void userDeletionCascadesCurrentAndLegacyDataButKeepsOtherUser() {
    User target = user();
    User other = user();
    service.save(target.getId(), new ConsentCreateRequest(values(false)));
    var otherState = service.save(other.getId(), new ConsentCreateRequest(values(true))).response();
    histories.saveAndFlush(new ConsentHistory(target, ConsentType.TERMS_OF_SERVICE, "legacy", true));
    withdrawal.deleteUserData(target.getId());
    em.clear();

    assertThat(repository.findByUserId(target.getId())).isEmpty();
    assertThat(histories.findByUserIdOrderByIdDesc(target.getId())).isEmpty();
    assertThat(service.get(other.getId())).isEqualTo(otherState);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentInitialPostsMergeDifferentFieldsWithoutDuplicateRows() throws Exception {
    UUID userId = user().getId();
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var futures = List.of(ConsentType.TERMS_OF_SERVICE, ConsentType.HEALTH_INFORMATION)
          .stream().map(type -> executor.submit(() -> {
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            return service.save(userId, new ConsentCreateRequest(Map.of(type, true)));
          })).toList();
      start.countDown();
      var first = futures.get(0).get(10, TimeUnit.SECONDS);
      var second = futures.get(1).get(10, TimeUnit.SECONDS);
      assertThat(List.of(first.created(), second.created())).containsExactlyInAnyOrder(true, false);
      assertThat(first.response().id()).isEqualTo(second.response().id());
      assertThat(service.get(userId).consents()).containsEntry(ConsentType.TERMS_OF_SERVICE, true)
          .containsEntry(ConsentType.HEALTH_INFORMATION, true);
      assertThat(histories.findByUserIdOrderByIdDesc(userId)).isEmpty();
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentPostAndPatchPreserveBothChanges() throws Exception {
    UUID userId = user().getId();
    service.save(userId, new ConsentCreateRequest(values(false)));
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var post = executor.submit(() -> {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return service.save(userId,
            new ConsentCreateRequest(Map.of(ConsentType.THIRD_PARTY_POLICY, true)));
      });
      var patch = executor.submit(() -> {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return service.update(userId,
            new ConsentUpdateRequest(Map.of(ConsentType.HEALTH_INFORMATION, true)));
      });
      start.countDown();
      assertThat(post.get(10, TimeUnit.SECONDS).created()).isFalse();
      patch.get(10, TimeUnit.SECONDS);
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
  void rollbackUndoesNewCurrentState() {
    UUID userId = user().getId();
    new TransactionTemplate(transactions).executeWithoutResult(status -> {
      service.save(userId, new ConsentCreateRequest(values(true)));
      status.setRollbackOnly();
    });
    assertThat(repository.findByUserId(userId)).isEmpty();
    assertThat(histories.findByUserIdOrderByIdDesc(userId)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void rollbackRestoresValuesAndModifiedTimeForPostAndPatch(boolean post) {
    UUID userId = user().getId();
    var initial = service.save(userId, new ConsentCreateRequest(values(false))).response();
    new TransactionTemplate(transactions).executeWithoutResult(status -> {
      var changed = Map.of(ConsentType.HEALTH_INFORMATION, true);
      if (post) {
        service.save(userId, new ConsentCreateRequest(changed));
      } else {
        service.update(userId, new ConsentUpdateRequest(changed));
      }
      status.setRollbackOnly();
    });
    assertThat(service.get(userId)).isEqualTo(initial);
  }

  @Test
  void databaseUniqueConstraintStillPreventsMultipleCurrentRows() {
    User user = user();
    repository.saveAndFlush(new Consent(user, values(false)));
    assertThatExceptionOfType(DataIntegrityViolationException.class)
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
