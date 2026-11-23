package polight.server.domain.consent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import polight.server.domain.consent.dto.ConsentCreateRequest;
import polight.server.domain.consent.dto.ConsentUpdateRequest;
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
  void requiresAllSevenValuesBeforeCreating() {
    assertInvalid(() -> service.create(userId, null));
    assertInvalid(() -> service.create(userId, new ConsentCreateRequest(null)));
    assertInvalid(() -> service.create(userId, new ConsentCreateRequest(Map.of())));
    assertInvalid(() -> service.create(userId,
        new ConsentCreateRequest(Map.of(ConsentType.TERMS_OF_SERVICE, true))));
    var values = allFalse();
    values.put(ConsentType.HEALTH_INFORMATION, null);
    assertInvalid(() -> service.create(userId, new ConsentCreateRequest(values)));
    verifyNoInteractions(repository);
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
  void duplicatePostIsConflictAndDoesNotOverwrite() {
    when(repository.lockUser(userId)).thenReturn(Optional.of(mock(User.class)));
    when(repository.existsByUserId(userId)).thenReturn(true);
    assertThatExceptionOfType(BaseException.class)
        .isThrownBy(() -> service.create(userId, new ConsentCreateRequest(allFalse())))
        .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CONSENT_ALREADY_EXISTS));
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
  void unauthenticatedReadsAreRejectedBeforeDatabaseAccess() {
    assertThatExceptionOfType(BaseException.class)
        .isThrownBy(() -> service.get(null))
        .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED));
    verifyNoInteractions(repository);
  }

  private EnumMap<ConsentType, Boolean> allFalse() {
    var values = new EnumMap<ConsentType, Boolean>(ConsentType.class);
    for (var type : ConsentType.values()) {
      values.put(type, false);
    }
    return values;
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
