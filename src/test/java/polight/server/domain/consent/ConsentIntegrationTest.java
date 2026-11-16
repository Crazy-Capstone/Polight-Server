package polight.server.domain.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static polight.server.domain.consent.entity.ConsentType.AGE_14_PLUS;
import static polight.server.domain.consent.entity.ConsentType.HEALTH_INFORMATION;
import static polight.server.domain.consent.entity.ConsentType.OVERSEAS_TRANSFER;
import static polight.server.domain.consent.entity.ConsentType.POLICY_ANALYSIS;
import static polight.server.domain.consent.entity.ConsentType.PRIVACY_COLLECTION_USE;
import static polight.server.domain.consent.entity.ConsentType.TERMS_OF_SERVICE;
import static polight.server.domain.consent.entity.ConsentType.THIRD_PARTY_POLICY;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import polight.server.domain.consent.dto.ConsentDecision;
import polight.server.domain.consent.entity.ConsentHistory;
import polight.server.domain.consent.entity.ConsentType;
import polight.server.domain.consent.repository.ConsentHistoryRepository;
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
  @Autowired private ConsentHistoryRepository histories;
  @Autowired private UserRepository users;
  @Autowired private UserWithdrawalRepository withdrawal;
  @Autowired private EntityManager entityManager;
  @Autowired private PlatformTransactionManager transactions;

  @Test
  void appendsEveryDecisionAndUsesLatestIdForRevocationAndReagreement() {
    User user = user();
    service.recordConsents(user, List.of(decision(TERMS_OF_SERVICE, true)));
    var first = histories.findFirstByUserIdAndConsentTypeOrderByIdDesc(
        user.getId(), TERMS_OF_SERVICE).orElseThrow();
    service.recordConsents(user, List.of(decision(TERMS_OF_SERVICE, false)));
    assertThat(service.getStatus(user).consents().get(TERMS_OF_SERVICE)).isFalse();
    service.recordConsents(user, List.of(decision(TERMS_OF_SERVICE, true)));
    entityManager.clear();

    assertThat(service.getStatus(user).consents().get(TERMS_OF_SERVICE)).isTrue();
    var history = histories.findByUserIdOrderByIdDesc(user.getId());
    assertThat(history).extracting(ConsentHistory::isAgreed).containsExactly(true, false, true);
    assertThat(history.get(2).getId()).isEqualTo(first.getId());
    assertThat(history.get(2).getCreatedAt()).isNotNull();
    assertThat(histories.findFirstByUserIdAndConsentTypeOrderByIdDesc(
        user.getId(), TERMS_OF_SERVICE).orElseThrow().getId()).isEqualTo(history.get(0).getId());
  }

  @Test
  void aggregatesOneSnapshotIncludingMissingOldAndOtherUsersDecisions() {
    User user = user();
    var empty = service.getStatus(user);
    assertThat(empty.consents()).hasSize(ConsentType.values().length);
    assertThat(empty.consents().values()).containsOnly(false);
    assertThat(empty.notAgreedTypes()).containsExactly(ConsentType.values());
    assertThat(empty.allAgreed()).isFalse();
    assertThat(empty.allRequiredAgreed()).isFalse();

    histories.save(new ConsentHistory(user, AGE_14_PLUS, "old", true));
    service.recordConsents(user, List.of(
        decision(TERMS_OF_SERVICE, true), decision(PRIVACY_COLLECTION_USE, true),
        decision(POLICY_ANALYSIS, true), decision(OVERSEAS_TRANSFER, false)));
    service.recordConsents(user(), List.of(decision(HEALTH_INFORMATION, true)));
    assertThat(service.getStatus(user).consents().get(AGE_14_PLUS)).isFalse();
    assertThat(service.getStatus(user).allRequiredAgreed()).isFalse();

    service.recordConsents(user, List.of(decision(AGE_14_PLUS, true)));
    var partial = service.getStatus(user);
    assertThat(partial.allAgreed()).isFalse();
    assertThat(partial.allRequiredAgreed()).isTrue();
    assertThat(partial.notAgreedTypes()).containsExactly(
        OVERSEAS_TRANSFER, THIRD_PARTY_POLICY, HEALTH_INFORMATION);
    var json = new ObjectMapper().valueToTree(partial);
    assertThat(json.path("consents").path(TERMS_OF_SERVICE.name()).asBoolean()).isTrue();
    assertThat(json.path("consents").path(HEALTH_INFORMATION.name()).asBoolean(true)).isFalse();

    service.recordConsents(user, Arrays.stream(ConsentType.values())
        .map(type -> decision(type, true)).toList());
    var complete = service.getStatus(user);
    assertThat(complete.allAgreed()).isTrue();
    assertThat(complete.allRequiredAgreed()).isTrue();
    assertThat(complete.notAgreedTypes()).isEmpty();
  }

  @Test
  void usesLatestDecisionEvenWhenAnEarlierDecisionMatchesTheCurrentVersion() {
    User user = user();
    service.recordConsents(user, List.of(decision(TERMS_OF_SERVICE, true)));
    histories.save(new ConsentHistory(user, TERMS_OF_SERVICE, "old", true));

    assertThat(service.getStatus(user).consents().get(TERMS_OF_SERVICE)).isFalse();
  }

  @Test
  void existingBulkUserDeletionCascadesOnlyThatUsersConsents() {
    User target = user();
    User other = user();
    service.recordConsents(target, List.of(decision(TERMS_OF_SERVICE, true)));
    service.recordConsents(other, List.of(decision(TERMS_OF_SERVICE, true)));

    withdrawal.deleteUserData(target.getId());
    entityManager.clear();

    assertThat(users.findById(target.getId())).isEmpty();
    assertThat(histories.findByUserIdOrderByIdDesc(target.getId())).isEmpty();
    assertThat(histories.findByUserIdOrderByIdDesc(other.getId())).hasSize(1);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void recordsConsentInTheSameTransactionAsUserCreation() {
    var transaction = new TransactionTemplate(transactions);
    UUID userId = transaction.execute(status -> {
      User user = user();
      service.recordConsents(user, List.of(decision(TERMS_OF_SERVICE, true)));
      assertThat(histories.findByUserIdOrderByIdDesc(user.getId())).hasSize(1);
      status.setRollbackOnly();
      return user.getId();
    });

    assertThat(users.findById(userId)).isEmpty();
    assertThat(histories.findByUserIdOrderByIdDesc(userId)).isEmpty();
  }

  private User user() {
    return users.save(User.builder().provider(User.Provider.KAKAO)
        .providerId(UUID.randomUUID().toString()).name("Tester").build());
  }

  private static ConsentDecision decision(ConsentType type, boolean agreed) {
    return new ConsentDecision(type, "dev-v1", agreed);
  }
}
