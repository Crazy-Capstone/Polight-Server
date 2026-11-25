package polight.server.domain.consent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import polight.server.domain.consent.dto.ConsentCreateRequest;
import polight.server.domain.consent.dto.ConsentUpdateRequest;
import polight.server.domain.consent.entity.Consent;
import polight.server.domain.consent.entity.ConsentType;
import polight.server.domain.consent.repository.ConsentRepository;
import polight.server.domain.user.entity.User;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

class ConsentServiceTest {

  private final ConsentRepository repository = mock(ConsentRepository.class);
  private final ConsentService service = new ConsentService(repository);
  private final UUID userId = UUID.randomUUID();

  @Test
  void rejectsEmptyOrExplicitNullValuesBeforeSaving() {
    assertInvalid(() -> service.save(userId, null));
    assertInvalid(() -> service.save(userId, new ConsentCreateRequest(null)));
    assertInvalid(() -> service.save(userId, new ConsentCreateRequest(Map.of())));
    var values = new HashMap<ConsentType, Boolean>();
    values.put(ConsentType.HEALTH_INFORMATION, null);
    assertInvalid(() -> service.save(userId, new ConsentCreateRequest(values)));
    verifyNoInteractions(repository);
  }

  @Test
  void partialPostCreatesCurrentStateAndPreservesUncollectedNulls() {
    when(repository.lockUser(userId)).thenReturn(Optional.of(mock(User.class)));
    when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var result = service.save(userId,
        new ConsentCreateRequest(Map.of(ConsentType.TERMS_OF_SERVICE, false)));

    assertThat(result.created()).isTrue();
    assertThat(result.response().consents()).hasSize(7)
        .containsEntry(ConsentType.TERMS_OF_SERVICE, false)
        .containsEntry(ConsentType.HEALTH_INFORMATION, null);
  }

  @Test
  void subsequentPostUpdatesOnlySubmittedValuesInsteadOfReturningConflict() {
    User user = mock(User.class);
    Consent consent = new Consent(user, Map.of(ConsentType.TERMS_OF_SERVICE, true));
    when(repository.lockUser(userId)).thenReturn(Optional.of(user));
    when(repository.findByUserId(userId)).thenReturn(Optional.of(consent));
    var result = service.save(userId,
        new ConsentCreateRequest(Map.of(ConsentType.HEALTH_INFORMATION, false)));

    assertThat(result.created()).isFalse();
    assertThat(result.response().consents()).containsEntry(ConsentType.TERMS_OF_SERVICE, true)
        .containsEntry(ConsentType.HEALTH_INFORMATION, false);
    verify(repository).flush();
  }

  @Test
  void rejectsEmptyNullAndInvalidPatchValues() {
    assertInvalid(() -> service.update(userId, null));
    assertInvalid(() -> service.update(userId, new ConsentUpdateRequest(null)));
    assertInvalid(() -> service.update(userId, new ConsentUpdateRequest(Map.of())));
    var nullKey = new HashMap<ConsentType, Boolean>();
    nullKey.put(null, true);
    assertInvalid(() -> service.update(userId, new ConsentUpdateRequest(nullKey)));
    verifyNoInteractions(repository);
  }

  @Test
  void currentStateSaveFailureIsPropagated() {
    when(repository.lockUser(userId)).thenReturn(Optional.of(mock(User.class)));
    when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("Test state failure"));
    assertThatExceptionOfType(DataIntegrityViolationException.class)
        .isThrownBy(() -> service.save(userId,
            new ConsentCreateRequest(Map.of(ConsentType.TERMS_OF_SERVICE, false))));
  }

  @Test
  void missingConsentIsNotFoundForGetAndPatch() {
    when(repository.findByUserId(userId)).thenReturn(Optional.empty());
    when(repository.lockUser(userId)).thenReturn(Optional.of(mock(User.class)));
    assertNotFound(() -> service.get(userId));
    assertNotFound(() -> service.update(userId,
        new ConsentUpdateRequest(Map.of(ConsentType.HEALTH_INFORMATION, true))));
    verify(repository).lockUser(userId);
  }

  @Test
  void unauthenticatedReadsAndWritesAreRejectedBeforeDatabaseAccess() {
    var values = Map.of(ConsentType.TERMS_OF_SERVICE, true);
    for (var call : java.util.List.<org.assertj.core.api.ThrowableAssert.ThrowingCallable>of(
        () -> service.get(null),
        () -> service.save(null, new ConsentCreateRequest(values)),
        () -> service.update(null, new ConsentUpdateRequest(values)))) {
      assertThatExceptionOfType(BaseException.class).isThrownBy(call)
          .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED));
    }
    verifyNoInteractions(repository);
  }

  private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
    assertThatExceptionOfType(BaseException.class).isThrownBy(call)
        .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
  }

  private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
    assertThatExceptionOfType(BaseException.class).isThrownBy(call)
        .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CONSENT_NOT_FOUND));
  }
}
