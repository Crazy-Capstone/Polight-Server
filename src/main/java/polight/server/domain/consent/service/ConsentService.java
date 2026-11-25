package polight.server.domain.consent.service;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.consent.dto.ConsentCreateRequest;
import polight.server.domain.consent.dto.ConsentResponse;
import polight.server.domain.consent.dto.ConsentUpdateRequest;
import polight.server.domain.consent.entity.Consent;
import polight.server.domain.consent.entity.ConsentType;
import polight.server.domain.consent.repository.ConsentRepository;
import polight.server.domain.user.entity.User;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class ConsentService {

  private final ConsentRepository repository;

  @Transactional
  public SaveResult save(UUID userId, ConsentCreateRequest request) {
    var consents = validate(request == null ? null : request.consents());
    User user = lockUser(userId);
    var existing = repository.findByUserId(userId);
    if (existing.isPresent()) {
      return new SaveResult(updateCurrent(existing.get(), consents), false);
    }
    Consent consent = repository.saveAndFlush(new Consent(user, consents));
    return new SaveResult(ConsentResponse.from(consent), true);
  }

  @Transactional
  public ConsentResponse update(UUID userId, ConsentUpdateRequest request) {
    var consents = validate(request == null ? null : request.consents());
    lockUser(userId);
    return updateCurrent(find(userId), consents);
  }

  @Transactional(readOnly = true)
  public ConsentResponse get(UUID userId) {
    return ConsentResponse.from(find(authenticatedId(userId)));
  }

  private ConsentResponse updateCurrent(Consent consent, Map<ConsentType, Boolean> consents) {
    consent.update(consents);
    repository.flush();
    return ConsentResponse.from(consent);
  }

  public record SaveResult(ConsentResponse response, boolean created) {}

  private Consent find(UUID userId) {
    return repository.findByUserId(userId)
        .orElseThrow(() -> new BaseException(ErrorCode.CONSENT_NOT_FOUND));
  }

  private User lockUser(UUID userId) {
    return repository.lockUser(authenticatedId(userId))
        .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
  }

  private UUID authenticatedId(UUID userId) {
    if (userId == null) {
      throw new BaseException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
    return userId;
  }

  private Map<ConsentType, Boolean> validate(Map<ConsentType, Boolean> consents) {
    if (consents == null || consents.isEmpty()
        || consents.keySet().stream().anyMatch(type -> type == null)
        || consents.values().stream().anyMatch(agreed -> agreed == null)) {
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }
    return consents;
  }
}
