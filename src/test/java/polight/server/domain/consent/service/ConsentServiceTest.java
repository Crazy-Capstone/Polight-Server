package polight.server.domain.consent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import polight.server.domain.consent.dto.ConsentCreateRequest;
import polight.server.domain.consent.dto.ConsentUpdateRequest;
import polight.server.domain.consent.entity.Consent;
import polight.server.domain.consent.entity.ConsentType;
import polight.server.domain.consent.repository.ConsentRepository;
import polight.server.domain.user.entity.User;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class ConsentServiceTest {

  // 입력 검증과 병합 규칙만 격리한다. 영속성·잠금·롤백은 ConsentIntegrationTest에서 검증한다.
  @Mock private ConsentRepository repository;
  @InjectMocks private ConsentService service;
  private final UUID userId = UUID.randomUUID();
  private final User user = User.builder().provider(User.Provider.KAKAO)
      .providerId("consent-user").name("동의 사용자").build();

  @Test
  @DisplayName("요청 자체가 null이면 POST와 PATCH 모두 DB 접근 전에 거부한다")
  void rejectsNullRequestsBeforeDatabaseAccess() {
    assertInvalid(() -> service.save(userId, null));
    assertInvalid(() -> service.update(userId, null));
    verifyNoInteractions(repository);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidConsents")
  @DisplayName("빈 동의정보나 null 키·값을 제출하면 POST와 PATCH 모두 DB 접근 전에 거부한다")
  void rejectsInvalidConsentsBeforeDatabaseAccess(String scenario, Map<ConsentType, Boolean> values) {
    assertInvalid(() -> service.save(userId, new ConsentCreateRequest(values)));
    assertInvalid(() -> service.update(userId, new ConsentUpdateRequest(values)));
    verifyNoInteractions(repository);
  }

  @Test
  @DisplayName("최초 POST에서 일부 항목에 미동의하면 나머지 모든 항목은 미수집으로 반환한다")
  void partialPostCreatesCurrentStateAndPreservesUncollectedNulls() {
    when(repository.lockUser(userId)).thenReturn(Optional.of(user));
    when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var result = service.save(userId,
        new ConsentCreateRequest(Map.of(ConsentType.TERMS_OF_SERVICE, false)));

    assertThat(result.created()).isTrue();
    var expected = uncollectedValues();
    expected.put(ConsentType.TERMS_OF_SERVICE, false);
    assertThat(result.response().consents()).isEqualTo(expected);
  }

  @Test
  @DisplayName("기존 동의정보에 POST하면 제출 항목만 갱신하고 기존 동의와 미수집 항목을 유지한다")
  void subsequentPostUpdatesOnlySubmittedValuesInsteadOfReturningConflict() {
    Consent consent = new Consent(user, Map.of(ConsentType.TERMS_OF_SERVICE, true));
    when(repository.lockUser(userId)).thenReturn(Optional.of(user));
    when(repository.findByUserId(userId)).thenReturn(Optional.of(consent));
    var result = service.save(userId,
        new ConsentCreateRequest(Map.of(ConsentType.HEALTH_INFORMATION, false)));

    assertThat(result.created()).isFalse();
    var expected = uncollectedValues();
    expected.put(ConsentType.TERMS_OF_SERVICE, true);
    expected.put(ConsentType.HEALTH_INFORMATION, false);
    assertThat(result.response().consents()).isEqualTo(expected);
    assertThat(consent.currentValues()).isEqualTo(expected);
  }

  @ParameterizedTest
  @EnumSource(ConsentType.class)
  @DisplayName("각 동의 항목을 PATCH하면 해당 항목만 바뀌고 나머지 항목은 유지한다")
  void patchUpdatesOnlySubmittedConsent(ConsentType type) {
    var before = new EnumMap<ConsentType, Boolean>(ConsentType.class);
    for (var consentType : ConsentType.values()) {
      before.put(consentType, false);
    }
    Consent consent = new Consent(user, before);
    when(repository.lockUser(userId)).thenReturn(Optional.of(user));
    when(repository.findByUserId(userId)).thenReturn(Optional.of(consent));

    var response = service.update(userId, new ConsentUpdateRequest(Map.of(type, true)));

    var expected = new EnumMap<>(before);
    expected.put(type, true);
    assertThat(response.consents()).isEqualTo(expected);
    assertThat(consent.currentValues()).isEqualTo(expected);
  }

  @Test
  @DisplayName("최초 동의정보 저장에 실패하면 성공 응답 대신 저장 예외를 전달한다")
  void currentStateSaveFailureIsPropagated() {
    when(repository.lockUser(userId)).thenReturn(Optional.of(user));
    when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("Test state failure"));
    assertThatExceptionOfType(DataIntegrityViolationException.class)
        .isThrownBy(() -> service.save(userId,
            new ConsentCreateRequest(Map.of(ConsentType.TERMS_OF_SERVICE, false))));
  }

  @Test
  @DisplayName("동의정보가 없으면 조회와 PATCH 모두 동의정보 없음 오류를 반환한다")
  void missingConsentIsNotFoundForGetAndPatch() {
    when(repository.findByUserId(userId)).thenReturn(Optional.empty());
    when(repository.lockUser(userId)).thenReturn(Optional.of(user));
    assertNotFound(() -> service.get(userId));
    assertNotFound(() -> service.update(userId,
        new ConsentUpdateRequest(Map.of(ConsentType.HEALTH_INFORMATION, true))));
  }

  @Test
  @DisplayName("존재하지 않는 사용자가 POST나 PATCH하면 사용자 없음 오류를 반환한다")
  void rejectsWritesForMissingUser() {
    when(repository.lockUser(userId)).thenReturn(Optional.empty());
    var values = Map.of(ConsentType.TERMS_OF_SERVICE, true);

    for (var call : java.util.List.<org.assertj.core.api.ThrowableAssert.ThrowingCallable>of(
        () -> service.save(userId, new ConsentCreateRequest(values)),
        () -> service.update(userId, new ConsentUpdateRequest(values)))) {
      assertThatExceptionOfType(BaseException.class).isThrownBy(call)
          .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }
  }

  @Test
  @DisplayName("미인증 사용자는 유효한 입력으로 조회·저장을 요청해도 DB 접근 전에 거부한다")
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

  private static Stream<Arguments> invalidConsents() {
    var nullKey = new HashMap<ConsentType, Boolean>();
    nullKey.put(null, true);
    var nullValue = new EnumMap<ConsentType, Boolean>(ConsentType.class);
    nullValue.put(ConsentType.HEALTH_INFORMATION, null);
    return Stream.of(
        Arguments.of("동의 맵이 null", null),
        Arguments.of("동의 맵이 비어 있음", Map.of()),
        Arguments.of("항목 키가 null", nullKey),
        Arguments.of("명시적인 null 값", nullValue));
  }

  private static EnumMap<ConsentType, Boolean> uncollectedValues() {
    var values = new EnumMap<ConsentType, Boolean>(ConsentType.class);
    for (var type : ConsentType.values()) {
      values.put(type, null);
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
