package polight.server.domain.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import polight.server.domain.analysis.dto.AnalysisCallbackRequest;
import polight.server.domain.analysis.dto.AnalysisCallbackRequest.CoverageItemPayload;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.analysis.entity.CoverageItem;
import polight.server.domain.analysis.service.AnalysisCallbackService;
import polight.server.domain.auth.service.RefreshTokenStore;
import polight.server.domain.chat.entity.ChatMessage;
import polight.server.domain.chat.entity.ChatSender;
import polight.server.domain.chat.entity.ChatSession;
import polight.server.domain.insurance.entity.DocumentKind;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.insurance.event.DocumentDeletionListener;
import polight.server.domain.insurance.mapper.PolicyDocumentMapper;
import polight.server.domain.insurance.service.PolicyDocumentService;
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
import polight.server.domain.trip.mapper.TripMapper;
import polight.server.domain.trip.repository.TripDeletionRepository;
import polight.server.domain.trip.service.TripDeletionService;
import polight.server.domain.trip.service.TripService;
import polight.server.domain.user.entity.User;
import polight.server.domain.user.service.UserService;
import polight.server.global.config.JpaAuditingConfig;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@DataJpaTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=${POLIGHT_TEST_DB_URL:jdbc:h2:mem:trip-delete;MODE=PostgreSQL;NON_KEYWORDS=VALUE}",
    "spring.datasource.driver-class-name=${POLIGHT_TEST_DB_DRIVER:org.h2.Driver}",
    "spring.datasource.username=${POLIGHT_TEST_DB_USER:sa}",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TripDeletionService.class, TripDeletionRepository.class, AnalysisCallbackService.class,
    DocumentDeletionListener.class, JpaAuditingConfig.class, PolicyDocumentService.class,
    PolicyDocumentMapper.class, TripService.class, TripMapper.class, UserService.class})
class TripDeletionIntegrationTest {

  @Autowired private TripDeletionService service;
  @Autowired private AnalysisCallbackService callbacks;
  @Autowired private PolicyDocumentService documentService;
  @Autowired private EntityManager em;
  @Autowired private PlatformTransactionManager transactionManager;
  @MockitoSpyBean private TripDeletionRepository deletionRepository;
  @MockitoBean private PolicyDocumentStorage storage;
  @MockitoBean private RefreshTokenStore refreshTokenStore;

  @Test
  void deletesOnlyTargetGraphAndPreservesSharedAndPrivateTermsAndAccountData() {
    User owner = user();
    User other = user();
    Scope target = scope(owner, "target.pdf");
    Scope sameUser = scope(owner, "same-user.pdf");
    Scope otherUser = scope(other, "other-user.pdf");
    PolicyDocument second = document(owner, target.trip(), "second.pdf");
    AnalysisResult secondAnalysis = persist(AnalysisResult.builder().document(second).build());
    CoverageItem secondCoverage = persist(CoverageItem.builder().analysisResult(secondAnalysis).title("second").build());
    PolicyChunk secondChunk = persist(PolicyChunk.builder().analysisResult(secondAnalysis).chunkIndex(0).content("private").build());
    PolicyDocument unassigned = document(owner, null, "unassigned.pdf");

    PolicyTerms shared = persist(PolicyTerms.fromUserUpload(target.document(), "insurer", "shared", "shared-hash"));
    shared.markVerified();
    PolicyTerms usedPrivate = persist(PolicyTerms.fromUserUpload(second, "insurer", "used-private", "used-hash"));
    PolicyTerms unusedPrivate = persist(PolicyTerms.fromUserUpload(second, "insurer", "unused-private", "unused-hash"));
    PolicyTerms official = persist(PolicyTerms.official("official", "product", "v1", null));
    PolicyTerms retainedSource = persist(PolicyTerms.fromUserUpload(sameUser.document(), "insurer", "retained", "retained-hash"));
    PolicyTermsCoverage rule = persist(PolicyTermsCoverage.builder().terms(shared).title("rule").build());
    PolicyTermsChunk chunk = persist(PolicyTermsChunk.builder().terms(shared).chunkIndex(0).content("terms").build());
    PolicyTermsCoverageSource source = persist(PolicyTermsCoverageSource.builder().termsCoverage(rule).termsChunk(chunk).build());
    CoverageDetailItem detail = persist(CoverageDetailItem.builder().termsCoverage(rule).title("detail").build());
    SubCoverageLimit limit = persist(SubCoverageLimit.builder().termsCoverage(rule).label("limit").value("100").build());
    RequiredDocument required = persist(RequiredDocument.builder().termsCoverage(rule).documentName("receipt").build());
    ExclusionCondition exclusion = persist(ExclusionCondition.builder().termsCoverage(rule).title("exclusion").build());
    sameUser.analysis().linkTerms(usedPrivate, "EXACT", null);
    otherUser.analysis().linkTerms(shared, "EXACT", null);
    otherUser.coverage().linkTermsCoverage(rule);
    Notification notification = persist(Notification.builder().user(owner).type(NotificationType.SYSTEM).title("title").body("body").build());
    NotificationPreference preference = persist(NotificationPreference.builder().user(owner).build());
    em.flush();
    em.createQuery("update PolicyChunk c set c.trip = null where c.id = :id")
        .setParameter("id", secondChunk.getId()).executeUpdate();

    service.deleteTrip(owner.getId(), target.trip().getId());

    assertDeleted(target);
    assertThat(em.find(PolicyDocument.class, second.getId())).isNull();
    assertThat(em.find(AnalysisResult.class, secondAnalysis.getId())).isNull();
    assertThat(em.find(CoverageItem.class, secondCoverage.getId())).isNull();
    assertThat(em.find(PolicyChunk.class, secondChunk.getId())).isNull();
    assertPreserved(sameUser);
    assertPreserved(otherUser);
    assertThat(em.find(PolicyDocument.class, unassigned.getId())).isNotNull();
    assertThat(em.find(User.class, owner.getId())).isNotNull();
    assertThat(em.find(User.class, other.getId())).isNotNull();
    assertThat(em.find(Notification.class, notification.getId())).isNotNull();
    assertThat(em.find(NotificationPreference.class, preference.getId())).isNotNull();
    for (PolicyTerms terms : List.of(shared, usedPrivate, unusedPrivate)) {
      PolicyTerms remaining = em.find(PolicyTerms.class, terms.getId());
      assertThat(remaining).isNotNull();
      assertThat(remaining.getSourceDocument()).isNull();
      assertThat(remaining.getOwnerUser().getId()).isEqualTo(owner.getId());
    }
    assertThat(em.find(PolicyTerms.class, official.getId())).isNotNull();
    assertThat(em.find(PolicyTerms.class, retainedSource.getId()).getSourceDocument().getId())
        .isEqualTo(sameUser.document().getId());
    assertThat(em.find(AnalysisResult.class, sameUser.analysis().getId()).getMatchedTerms().getId()).isEqualTo(usedPrivate.getId());
    assertThat(em.find(AnalysisResult.class, otherUser.analysis().getId()).getMatchedTerms().getId()).isEqualTo(shared.getId());
    assertThat(em.find(CoverageItem.class, otherUser.coverage().getId()).getTermsCoverage().getId()).isEqualTo(rule.getId());
    assertThat(em.find(PolicyTermsCoverageSource.class, source.getId())).isNotNull();
    assertThat(em.find(PolicyTermsChunk.class, chunk.getId())).isNotNull();
    assertThat(em.find(CoverageDetailItem.class, detail.getId())).isNotNull();
    assertThat(em.find(SubCoverageLimit.class, limit.getId())).isNotNull();
    assertThat(em.find(RequiredDocument.class, required.getId())).isNotNull();
    assertThat(em.find(ExclusionCondition.class, exclusion.getId())).isNotNull();
    verifyNoInteractions(storage, refreshTokenStore);
  }

  @Test
  void missingAndForeignTripsDoNotChangeData() {
    User owner = user();
    Scope target = scope(user(), "other.pdf");
    assertError(() -> service.deleteTrip(owner.getId(), target.trip().getId()), ErrorCode.TRIP_NOT_FOUND);
    assertError(() -> service.deleteTrip(owner.getId(), UUID.randomUUID()), ErrorCode.TRIP_NOT_FOUND);
    assertError(() -> service.deleteTrip(null, target.trip().getId()), ErrorCode.AUTHENTICATION_REQUIRED);
    assertPreserved(target);
    verifyNoInteractions(storage, refreshTokenStore);
  }

  @Test
  void deletesTripWithoutDocuments() {
    User owner = user();
    Trip trip = trip(owner);
    service.deleteTrip(owner.getId(), trip.getId());
    assertThat(em.find(Trip.class, trip.getId())).isNull();
    assertThat(em.find(User.class, owner.getId())).isNotNull();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DirtiesContext
  void deletesDistinctFilePathsOnlyAfterCommit() {
    Scope target = tx().execute(status -> {
      Scope value = scope(user(), "first.pdf");
      document(value.trip().getUser(), value.trip(), "first.pdf");
      document(value.trip().getUser(), value.trip(), "second.pdf");
      return value;
    });
    doAnswer(invocation -> {
      tx().executeWithoutResult(status -> assertDeleted(target));
      return null;
    }).when(storage).delete(anyString());

    service.deleteTrip(target.trip().getUser().getId(), target.trip().getId());

    verify(storage, times(1)).delete("first.pdf");
    verify(storage, times(1)).delete("second.pdf");
    verifyNoInteractions(refreshTokenStore);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DirtiesContext
  void repositoryFailureRollsBackAllDeletesAndSourceReference() {
    Scope target = tx().execute(status -> scope(user(), "rollback.pdf"));
    UUID termsId = tx().execute(status -> persist(PolicyTerms.fromUserUpload(
        em.find(PolicyDocument.class, target.document().getId()), "insurer", "rollback", "hash")).getId());
    doAnswer(invocation -> {
      invocation.callRealMethod();
      throw new IllegalStateException("forced database failure");
    }).when(deletionRepository).deleteTripData(any(), any());

    assertThatThrownBy(() -> service.deleteTrip(target.trip().getUser().getId(), target.trip().getId()))
        .isInstanceOf(IllegalStateException.class);

    tx().executeWithoutResult(status -> {
      assertPreserved(target);
      assertThat(em.find(PolicyTerms.class, termsId).getSourceDocument().getId()).isEqualTo(target.document().getId());
    });
    verifyNoInteractions(storage);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DirtiesContext
  void outerRollbackSuppressesAlreadyPublishedDeletionEvent() {
    Scope target = tx().execute(status -> scope(user(), "rollback-event.pdf"));
    assertThatThrownBy(() -> tx().executeWithoutResult(status -> {
      service.deleteTrip(target.trip().getUser().getId(), target.trip().getId());
      throw new IllegalStateException("outer rollback");
    })).isInstanceOf(IllegalStateException.class);
    tx().executeWithoutResult(status -> assertPreserved(target));
    verifyNoInteractions(storage);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DirtiesContext
  void fileFailureDoesNotUndoDeletionAndOtherFilesAreStillDeleted() {
    Scope target = tx().execute(status -> {
      Scope value = scope(user(), "failed.pdf");
      document(value.trip().getUser(), value.trip(), "success.pdf");
      return value;
    });
    doThrow(new IllegalStateException("S3 unavailable")).when(storage).delete("failed.pdf");
    service.deleteTrip(target.trip().getUser().getId(), target.trip().getId());
    tx().executeWithoutResult(status -> assertDeleted(target));
    verify(storage).delete("failed.pdf");
    verify(storage).delete("success.pdf");
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DirtiesContext
  void lateCompleteAndFailureCallbacksCannotRecreateDeletedData() {
    Scope target = tx().execute(status -> scope(user(), "late.pdf"));
    service.deleteTrip(target.trip().getUser().getId(), target.trip().getId());
    assertError(() -> callbacks.complete(target.analysis().getId(), payload(target.analysis().getId()), "{}"),
        ErrorCode.ANALYSIS_RESULT_NOT_FOUND);
    assertError(() -> callbacks.fail(target.analysis().getId(), "late failure"), ErrorCode.ANALYSIS_RESULT_NOT_FOUND);
    tx().executeWithoutResult(status -> assertDeleted(target));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DirtiesContext
  void callbackFirstCommitsBeforeDeletionRemovesNewCoverage() throws Exception {
    Scope target = tx().execute(status -> scope(user(), "callback-first.pdf"));
    var callbackReady = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var deletionEntered = new CountDownLatch(1);
    doAnswer(invocation -> {
      deletionEntered.countDown();
      return invocation.callRealMethod();
    }).when(deletionRepository).deleteTripData(any(), any());
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      var callback = executor.submit(() -> tx().executeWithoutResult(status -> {
        callbacks.complete(target.analysis().getId(), payload(target.analysis().getId()), "{}");
        callbackReady.countDown();
        await(release);
      }));
      await(callbackReady);
      var deletion = executor.submit(() -> service.deleteTrip(target.trip().getUser().getId(), target.trip().getId()));
      await(deletionEntered);
      assertThatThrownBy(() -> deletion.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
      release.countDown();
      callback.get(10, TimeUnit.SECONDS);
      deletion.get(10, TimeUnit.SECONDS);
      tx().executeWithoutResult(status -> {
        assertDeleted(target);
        assertThat(count(CoverageItem.class)).isZero();
      });
    } finally {
      release.countDown();
      stop(executor);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DirtiesContext
  void deletionFirstMakesWaitingCallbackReturnNotFound() throws Exception {
    Scope target = tx().execute(status -> scope(user(), "delete-first.pdf"));
    var deleted = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var callbackEntered = new CountDownLatch(1);
    doAnswer(invocation -> {
      invocation.callRealMethod();
      deleted.countDown();
      await(release);
      return null;
    }).when(deletionRepository).deleteTripData(any(), any());
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      var deletion = executor.submit(() -> service.deleteTrip(target.trip().getUser().getId(), target.trip().getId()));
      await(deleted);
      var callback = executor.submit(() -> {
        callbackEntered.countDown();
        callbacks.complete(target.analysis().getId(), payload(target.analysis().getId()), "{}");
      });
      await(callbackEntered);
      assertThatThrownBy(() -> callback.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
      release.countDown();
      deletion.get(10, TimeUnit.SECONDS);
      assertThatThrownBy(() -> callback.get(10, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(BaseException.class)
          .satisfies(error -> assertThat(((BaseException) error.getCause()).getErrorCode())
              .isEqualTo(ErrorCode.ANALYSIS_RESULT_NOT_FOUND));
      tx().executeWithoutResult(status -> assertDeleted(target));
    } finally {
      release.countDown();
      stop(executor);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DirtiesContext
  void uploadFirstIsIncludedInWaitingDeletion() throws Exception {
    Scope target = tx().execute(status -> scope(user(), "original.pdf"));
    var uploaded = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    doAnswer(invocation -> {
      uploaded.countDown();
      await(release);
      return "uploaded.pdf";
    }).when(storage).store(any());
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      var upload = executor.submit(() -> documentService.uploadDocument(
          target.trip().getUser().getId(), target.trip().getId(),
          new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[] {1}), DocumentKind.TERMS));
      await(uploaded);
      var deletion = executor.submit(() -> service.deleteTrip(target.trip().getUser().getId(), target.trip().getId()));
      assertThatThrownBy(() -> deletion.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
      release.countDown();
      upload.get(10, TimeUnit.SECONDS);
      deletion.get(10, TimeUnit.SECONDS);
      tx().executeWithoutResult(status -> assertThat(count(PolicyDocument.class)).isZero());
      verify(storage).delete("uploaded.pdf");
      verify(storage).delete("original.pdf");
    } finally {
      release.countDown();
      stop(executor);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DirtiesContext
  void deletionFirstRejectsWaitingUploadBeforeFileStorage() throws Exception {
    Scope target = tx().execute(status -> scope(user(), "delete-before-upload.pdf"));
    var deleted = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    doAnswer(invocation -> {
      invocation.callRealMethod();
      deleted.countDown();
      await(release);
      return null;
    }).when(deletionRepository).deleteTripData(any(), any());
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      var deletion = executor.submit(() -> service.deleteTrip(target.trip().getUser().getId(), target.trip().getId()));
      await(deleted);
      var upload = executor.submit(() -> documentService.uploadDocumentTo(target.trip(),
          new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[] {1}), DocumentKind.TERMS));
      assertThatThrownBy(() -> upload.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
      release.countDown();
      deletion.get(10, TimeUnit.SECONDS);
      assertThatThrownBy(() -> upload.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(BaseException.class);
      verify(storage, never()).store(any());
      tx().executeWithoutResult(status -> assertThat(count(PolicyDocument.class)).isZero());
    } finally {
      release.countDown();
      stop(executor);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DirtiesContext
  void concurrentDeletionHasOneSuccessAndOneNotFound() throws Exception {
    Scope target = tx().execute(status -> scope(user(), "concurrent.pdf"));
    var deleted = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    doAnswer(invocation -> {
      invocation.callRealMethod();
      deleted.countDown();
      await(release);
      return null;
    }).when(deletionRepository).deleteTripData(any(), any());
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      var first = executor.submit(() -> service.deleteTrip(target.trip().getUser().getId(), target.trip().getId()));
      await(deleted);
      var second = executor.submit(() -> service.deleteTrip(target.trip().getUser().getId(), target.trip().getId()));
      assertThatThrownBy(() -> second.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
      release.countDown();
      first.get(10, TimeUnit.SECONDS);
      assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(BaseException.class)
          .satisfies(error -> assertThat(((BaseException) error.getCause()).getErrorCode()).isEqualTo(ErrorCode.TRIP_NOT_FOUND));
      verify(storage, times(1)).delete("concurrent.pdf");
    } finally {
      release.countDown();
      stop(executor);
    }
  }

  private Scope scope(User owner, String path) {
    Trip trip = trip(owner);
    PolicyDocument document = document(owner, trip, path);
    AnalysisResult analysis = persist(AnalysisResult.builder().document(document).build());
    CoverageItem coverage = persist(CoverageItem.builder().analysisResult(analysis).title("coverage").build());
    PolicyChunk chunk = persist(PolicyChunk.builder().analysisResult(analysis).chunkIndex(0).content("private").build());
    ChatSession session = persist(ChatSession.builder().user(owner).trip(trip).title("chat").build());
    ChatMessage message = persist(ChatMessage.builder().session(session).sender(ChatSender.USER).content("private").build());
    return new Scope(trip, document, analysis, coverage, chunk, session, message);
  }

  private User user() {
    return persist(User.builder().provider(User.Provider.KAKAO).providerId(UUID.randomUUID().toString()).name("tester").build());
  }

  private Trip trip(User owner) {
    return persist(Trip.builder().user(owner).name("Trip").startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(1)).build());
  }

  private PolicyDocument document(User owner, Trip trip, String path) {
    return persist(PolicyDocument.builder().user(owner).trip(trip).originalFilename("test.pdf")
        .storedFilePath(path).documentKind(DocumentKind.TERMS).build());
  }

  private <T> T persist(T entity) {
    em.persist(entity);
    return entity;
  }

  private void assertDeleted(Scope scope) {
    assertThat(em.find(Trip.class, scope.trip().getId())).isNull();
    assertThat(em.find(PolicyDocument.class, scope.document().getId())).isNull();
    assertThat(em.find(AnalysisResult.class, scope.analysis().getId())).isNull();
    assertThat(em.find(CoverageItem.class, scope.coverage().getId())).isNull();
    assertThat(em.find(PolicyChunk.class, scope.chunk().getId())).isNull();
    assertThat(em.find(ChatSession.class, scope.session().getId())).isNull();
    assertThat(em.find(ChatMessage.class, scope.message().getId())).isNull();
  }

  private void assertPreserved(Scope scope) {
    assertThat(em.find(Trip.class, scope.trip().getId())).isNotNull();
    assertThat(em.find(PolicyDocument.class, scope.document().getId())).isNotNull();
    assertThat(em.find(AnalysisResult.class, scope.analysis().getId())).isNotNull();
    assertThat(em.find(CoverageItem.class, scope.coverage().getId())).isNotNull();
    assertThat(em.find(PolicyChunk.class, scope.chunk().getId())).isNotNull();
    assertThat(em.find(ChatSession.class, scope.session().getId())).isNotNull();
    assertThat(em.find(ChatMessage.class, scope.message().getId())).isNotNull();
  }

  private void assertError(Runnable action, ErrorCode code) {
    assertThatThrownBy(action::run).isInstanceOf(BaseException.class)
        .satisfies(error -> assertThat(((BaseException) error).getErrorCode()).isEqualTo(code));
  }

  private AnalysisCallbackRequest payload(UUID id) {
    return new AnalysisCallbackRequest(id, "COMPLETED", "summary", null, null, null, null,
        true, null, null, null, null, List.of(new CoverageItemPayload(
            "new coverage", null, null, "COVERED", null, 100L, "KRW", null, null, null, null, null)));
  }

  private TransactionTemplate tx() {
    return new TransactionTemplate(transactionManager);
  }

  private long count(Class<?> entity) {
    return em.createQuery("select count(e) from " + entity.getSimpleName() + " e", Long.class).getSingleResult();
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting for concurrent test operation");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  private static void stop(ExecutorService executor) throws InterruptedException {
    executor.shutdownNow();
    assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
  }

  private record Scope(Trip trip, PolicyDocument document, AnalysisResult analysis,
      CoverageItem coverage, PolicyChunk chunk, ChatSession session, ChatMessage message) {}
}
