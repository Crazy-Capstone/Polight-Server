package polight.server.domain.consent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static polight.server.domain.consent.entity.ConsentType.AGE_14_PLUS;
import static polight.server.domain.consent.entity.ConsentType.HEALTH_INFORMATION;
import static polight.server.domain.consent.entity.ConsentType.PRIVACY_COLLECTION_USE;
import static polight.server.domain.consent.entity.ConsentType.TERMS_OF_SERVICE;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import polight.server.domain.consent.dto.ConsentDecision;
import polight.server.domain.consent.entity.ConsentType;
import polight.server.domain.consent.repository.ConsentHistoryRepository;
import polight.server.domain.user.entity.User;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

class ConsentServiceTest {

  private final ConsentHistoryRepository histories = mock(ConsentHistoryRepository.class);
  private final ConsentService service = new ConsentService(histories);

  @Test
  void acceptsAllRequiredDecisionsEvenWithoutOptionalDecisions() {
    assertThatNoException().isThrownBy(() -> service.validateRequiredConsents(required()));
    assertThatNoException().isThrownBy(() -> service.validateRequiredConsents(List.of(
        decision(TERMS_OF_SERVICE, true), decision(PRIVACY_COLLECTION_USE, true),
        decision(AGE_14_PLUS, true), decision(HEALTH_INFORMATION, false))));
    verifyNoInteractions(histories);
  }

  @Test
  void identifiesEveryMissingOrDeclinedRequiredType() {
    assertThatExceptionOfType(BaseException.class)
        .isThrownBy(() -> service.validateRequiredConsents(List.of(
            decision(TERMS_OF_SERVICE, true), decision(AGE_14_PLUS, false))))
        .satisfies(error -> {
          assertThat(error.getErrorCode()).isEqualTo(ErrorCode.REQUIRED_CONSENT_NOT_AGREED);
          assertThat(error.getFieldErrors()).containsOnlyKeys(
              PRIVACY_COLLECTION_USE.name(), AGE_14_PLUS.name());
        });
    verifyNoInteractions(histories);
  }

  @Test
  void rejectsWrongVersionDuplicateTypeAndMissingDecisionBeforeWriting() {
    User user = mock(User.class);
    when(user.getId()).thenReturn(UUID.randomUUID());
    List<List<ConsentDecision>> invalid = List.of(
        List.of(new ConsentDecision(TERMS_OF_SERVICE, "old", true)),
        List.of(new ConsentDecision(TERMS_OF_SERVICE, null, true)),
        List.of(decision(TERMS_OF_SERVICE, true), decision(TERMS_OF_SERVICE, false)),
        List.of(new ConsentDecision(TERMS_OF_SERVICE, "dev-v1", null)),
        List.of(new ConsentDecision(null, "dev-v1", true)));
    for (var decisions : invalid) {
      assertThatExceptionOfType(BaseException.class)
          .isThrownBy(() -> service.recordConsents(user, decisions))
          .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }
    verifyNoInteractions(histories);
  }

  private static List<ConsentDecision> required() {
    return List.of(decision(TERMS_OF_SERVICE, true), decision(PRIVACY_COLLECTION_USE, true),
        decision(AGE_14_PLUS, true));
  }

  private static ConsentDecision decision(ConsentType type, boolean agreed) {
    return new ConsentDecision(type, "dev-v1", agreed);
  }
}
