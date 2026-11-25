package polight.server.domain.consent.service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.consent.dto.ConsentCreateRequest;
import polight.server.domain.consent.dto.ConsentResponse;
import polight.server.domain.consent.dto.ConsentUpdateRequest;
import polight.server.domain.consent.entity.Consent;
import polight.server.domain.consent.entity.ConsentHistory;
import polight.server.domain.consent.entity.ConsentType;
import polight.server.domain.consent.repository.ConsentRepository;
import polight.server.domain.consent.repository.ConsentHistoryRepository;
import polight.server.domain.user.entity.User;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class ConsentService {

  private final ConsentRepository repository;
  private final ConsentHistoryRepository histories;
  private final ConsentDocumentPolicy documentPolicy;

  @Transactional
  public ConsentResponse create(UUID userId, ConsentCreateRequest request) {
    var consents = validate(request == null ? null : request.consents());
    User user = lockUser(userId);
    if (repository.existsByUserId(userId)) {
      throw new BaseException(ErrorCode.CONSENT_ALREADY_EXISTS);
    }
    Consent consent = repository.saveAndFlush(new Consent(user, consents));
    histories.saveAllAndFlush(consents.entrySet().stream()
        .map(entry -> new ConsentHistory(
            user, entry.getKey(), documentPolicy.currentVersion(entry.getKey()), entry.getValue()))
        .toList());
    return ConsentResponse.from(consent);
  }

  @Transactional
  public ConsentResponse update(UUID userId, ConsentUpdateRequest request) {
    var consents = validate(request == null ? null : request.consents());
    User user = lockUser(userId);
    Consent consent = find(userId);
    Map<ConsentType, Boolean> currentValues = consent.currentValues();
    var latestByType = new EnumMap<ConsentType, ConsentHistory>(ConsentType.class);
    histories.findLatestByUserId(userId).forEach(history -> latestByType.put(history.getConsentType(), history));
    var changes = new EnumMap<ConsentType, Boolean>(ConsentType.class);
    List<ConsentHistory> newHistory = new ArrayList<>();
    consents.forEach((type, agreed) -> {
      boolean valueChanged = !Objects.equals(currentValues.get(type), agreed);
      String version = documentPolicy.currentVersion(type);
      ConsentHistory latest = latestByType.get(type);
      boolean versionChanged = latest != null && !version.equals(latest.getDocumentVersion());
      if (valueChanged) {
        changes.put(type, agreed);
      }
      if (valueChanged || versionChanged) {
        newHistory.add(new ConsentHistory(user, type, version, agreed));
      }
    });
    if (!changes.isEmpty()) {
      consent.update(changes);
      repository.flush();
    }
    if (!newHistory.isEmpty()) {
      histories.saveAllAndFlush(newHistory);
    }
    return ConsentResponse.from(consent);
  }

  @Transactional(readOnly = true)
  public ConsentResponse get(UUID userId) {
    return ConsentResponse.from(find(authenticatedId(userId)));
  }

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
