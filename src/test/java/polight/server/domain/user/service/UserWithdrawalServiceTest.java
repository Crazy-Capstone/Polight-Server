package polight.server.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyString;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.analysis.entity.CoverageItem;
import polight.server.domain.auth.service.RefreshTokenStore;
import polight.server.domain.chat.entity.ChatMessage;
import polight.server.domain.chat.entity.ChatSender;
import polight.server.domain.chat.entity.ChatSession;
import polight.server.domain.insurance.entity.DocumentKind;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.insurance.event.DocumentDeletionListener;
import polight.server.domain.insurance.storage.PolicyDocumentStorage;
import polight.server.domain.notification.entity.Notification;
import polight.server.domain.notification.entity.NotificationPreference;
import polight.server.domain.notification.entity.NotificationType;
import polight.server.domain.rag.entity.PolicyChunk;
import polight.server.domain.terms.entity.CoverageDetailItem;
import polight.server.domain.terms.entity.ExclusionCondition;
import polight.server.domain.terms.entity.PolicyTerms;
import polight.server.domain.terms.entity.PolicyTermsChunk;
import polight.server.domain.terms.entity.PolicyTermsCoverage;
import polight.server.domain.terms.entity.PolicyTermsCoverageSource;
import polight.server.domain.terms.entity.RequiredDocument;
import polight.server.domain.terms.entity.SubCoverageLimit;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.repository.UserRepository;
import polight.server.domain.user.repository.UserWithdrawalRepository;
import polight.server.global.config.JpaAuditingConfig;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:withdrawal;MODE=PostgreSQL;NON_KEYWORDS=VALUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({UserWithdrawalService.class, UserWithdrawalRepository.class, JpaAuditingConfig.class,
    DocumentDeletionListener.class})
class UserWithdrawalServiceTest {

  @Autowired private UserWithdrawalService service;
  @Autowired private UserWithdrawalRepository withdrawalRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private EntityManager em;
  @Autowired private PlatformTransactionManager transactionManager;
  @MockitoBean private RefreshTokenStore refreshTokenStore;
  @MockitoBean private PolicyDocumentStorage storage;

  @Test
  void deletesEntireOwnedGraphAndKeepsOtherUsersAndOfficialTerms() {
    User user = user("withdrawn");
    User other = user("remaining");
    Trip trip = persist(Trip.builder().user(user).name("Trip")
        .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(3)).build());
    PolicyDocument document = document(user, trip, "owned-file");
    // Documents without a trip must also be removed.
    document(user, null, "unassigned-file");
    AnalysisResult analysis = persist(AnalysisResult.builder().document(document).build());
    persist(PolicyChunk.builder().analysisResult(analysis).chunkIndex(0).content("private").build());
    persist(CoverageItem.builder().analysisResult(analysis).title("insured").build());
    ChatSession session = persist(ChatSession.builder().user(user).trip(trip).title("chat").build());
    persist(ChatMessage.builder().session(session).sender(ChatSender.USER).content("private").build());
    persist(Notification.builder().user(user).type(NotificationType.SYSTEM).title("title").body("body").build());
    persist(NotificationPreference.builder().user(user).build());

    PolicyTerms terms = persist(PolicyTerms.fromUserUpload(document, "insurer", "product", "hash"));
    terms.markVerified();
    PolicyTermsCoverage rule = persist(PolicyTermsCoverage.builder().terms(terms).title("rule").build());
    PolicyTermsChunk chunk = persist(PolicyTermsChunk.builder().terms(terms).chunkIndex(0).content("terms").build());
    persist(PolicyTermsCoverageSource.builder().termsCoverage(rule).termsChunk(chunk).build());
    persist(CoverageDetailItem.builder().termsCoverage(rule).title("detail").build());
    persist(SubCoverageLimit.builder().termsCoverage(rule).label("limit").value("100").build());
    persist(RequiredDocument.builder().termsCoverage(rule).documentName("receipt").build());
    persist(ExclusionCondition.builder().termsCoverage(rule).title("exclusion").build());
    PolicyTerms official = persist(PolicyTerms.official("official", "product", "v1", null));
    // Exercise a terms row with an owner but no source document (LEFT JOIN is necessary).
    persist(PolicyTerms.builder().ownerUser(user).insurerName("private").productName("manual").build());

    PolicyDocument otherDocument = document(other, null, "other-file");
    AnalysisResult otherAnalysis = persist(AnalysisResult.builder().document(otherDocument).build());
    otherAnalysis.linkTerms(terms, "EXACT", "notice");
    CoverageItem otherCoverage = persist(CoverageItem.builder().analysisResult(otherAnalysis).title("other insured").limitAmount(123L).build());
    otherCoverage.linkTermsCoverage(rule);
    ChatSession otherSession = persist(ChatSession.builder().user(other).title("other chat").build());
    ChatMessage otherMessage = persist(ChatMessage.builder().session(otherSession).sender(ChatSender.USER).content("keep").build());
    UUID userId = user.getId();
    em.flush();

    service.withdraw(userId);

    assertThat(userRepository.existsById(userId)).isFalse();
    assertThat(userRepository.existsById(other.getId())).isTrue();
    for (Class<?> entity : List.of(Trip.class, PolicyChunk.class, Notification.class,
        NotificationPreference.class, PolicyTermsCoverage.class, PolicyTermsChunk.class,
        PolicyTermsCoverageSource.class, CoverageDetailItem.class, SubCoverageLimit.class,
        RequiredDocument.class, ExclusionCondition.class)) {
      assertThat(em.createQuery("select count(e) from " + entity.getSimpleName() + " e", Long.class)
          .getSingleResult()).as(entity.getSimpleName()).isZero();
    }
    assertThat(em.find(PolicyDocument.class, document.getId())).isNull();
    assertThat(em.find(AnalysisResult.class, analysis.getId())).isNull();
    assertThat(em.find(ChatSession.class, session.getId())).isNull();
    assertThat(em.find(ChatMessage.class, otherMessage.getId())).isNotNull();
    assertThat(em.find(PolicyTerms.class, terms.getId())).isNull();
    assertThat(em.find(PolicyTerms.class, official.getId())).isNotNull();
    assertThat(em.find(AnalysisResult.class, otherAnalysis.getId()).getMatchedTerms()).isNull();
    assertThat(em.find(AnalysisResult.class, otherAnalysis.getId()).getTermsMatchStage()).isNull();
    CoverageItem remainingCoverage = em.find(CoverageItem.class, otherCoverage.getId());
    assertThat(remainingCoverage.getTermsCoverage()).isNull();
    assertThat(remainingCoverage.getLimitAmount()).isEqualTo(123L);
    // The surrounding test transaction has not committed yet.
    verifyNoInteractions(storage);
    verify(refreshTokenStore).revokeAll(userId);
  }

  @Test
  void deletesUserWithNoRelatedData() {
    User user = user("empty");
    UUID id = user.getId();
    service.withdraw(id);
    assertThat(userRepository.existsById(id)).isFalse();
    verifyNoInteractions(storage);
  }

  @Test
  void rejectsUnknownAndUnauthenticatedUsers() {
    assertThatThrownBy(() -> service.withdraw(UUID.randomUUID()))
        .isInstanceOf(BaseException.class)
        .extracting(e -> ((BaseException) e).getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
    assertThatThrownBy(() -> service.withdraw(null))
        .isInstanceOf(BaseException.class)
        .extracting(e -> ((BaseException) e).getErrorCode()).isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DirtiesContext
  void rollsBackDatabaseWithoutDeletingFilesWhenTransactionFails() {
    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    UUID userId = tx.execute(status -> {
      User user = user("rollback");
      document(user, null, "rollback-file");
      return user.getId();
    });

    assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
      service.withdraw(userId);
      throw new IllegalStateException("force rollback after deletion");
    })).isInstanceOf(IllegalStateException.class);

    assertThat(userRepository.existsById(userId)).isTrue();
    verifyNoInteractions(storage);
    tx.executeWithoutResult(status -> {
      assertThat(em.createQuery("select d from PolicyDocument d where d.user.id = :id", PolicyDocument.class)
          .setParameter("id", userId).getResultList()).hasSize(1);
      withdrawalRepository.deleteUserData(userId);
    });
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DirtiesContext
  void deletesOnlyOwnedFilesAfterDatabaseCommit() {
    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    UUID userId = tx.execute(status -> {
      User user = user("committed");
      document(user, null, "first-file");
      document(user, null, "second-file");
      User other = user("other-committed");
      document(other, null, "other-file");
      return user.getId();
    });
    doAnswer(invocation -> {
      assertThat(userRepository.existsById(userId)).isFalse();
      return null;
    }).when(storage).delete(anyString());

    service.withdraw(userId);

    verify(storage).delete("first-file");
    verify(storage).delete("second-file");
    verify(storage, never()).delete("other-file");
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DirtiesContext
  void fileDeletionFailureDoesNotUndoWithdrawal() {
    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    UUID userId = tx.execute(status -> {
      User user = user("file-failure");
      document(user, null, "failed-file");
      return user.getId();
    });
    doThrow(new IllegalStateException("Storage unavailable")).when(storage).delete("failed-file");

    service.withdraw(userId);

    assertThat(userRepository.existsById(userId)).isFalse();
    verify(storage).delete("failed-file");
  }

  @Test
  void redisFailureDoesNotDeleteUser() {
    User user = user("redis-failure");
    doThrow(new IllegalStateException("Redis unavailable")).when(refreshTokenStore).revokeAll(user.getId());
    assertThatThrownBy(() -> service.withdraw(user.getId())).isInstanceOf(IllegalStateException.class);
    assertThat(userRepository.existsById(user.getId())).isTrue();
  }

  private User user(String providerId) {
    return persist(User.builder().provider(User.Provider.KAKAO).providerId(providerId).name(providerId).build());
  }

  private PolicyDocument document(User user, Trip trip, String path) {
    return persist(PolicyDocument.builder().user(user).trip(trip).originalFilename("file.pdf")
        .storedFilePath(path).documentKind(DocumentKind.TERMS).build());
  }

  private <T> T persist(T entity) {
    em.persist(entity);
    return entity;
  }
}
