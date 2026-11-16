package polight.server.domain.consent.service;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.consent.dto.ConsentDecision;
import polight.server.domain.consent.dto.ConsentStatusResponse;
import polight.server.domain.consent.entity.ConsentHistory;
import polight.server.domain.consent.entity.ConsentType;
import polight.server.domain.consent.repository.ConsentHistoryRepository;
import polight.server.domain.user.entity.User;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class ConsentService {

  private final ConsentHistoryRepository histories;

  // 회원 생성 전 호출, 필수 동의 거절/누락 시 에러 반환
  public void validateRequiredConsents(List<ConsentDecision> decisions) {
    var indexed = validateDecisions(decisions == null ? List.of() : decisions);
    Map<String, String> missing = new LinkedHashMap<>();
    for (var type : ConsentPolicy.REQUIRED_AT_SIGNUP) {
      var decision = indexed.get(type);
      if (decision == null || !decision.agreed()) {
        missing.put(type.name(), "필수 동의 항목에 동의해야 합니다.");
      }
    }
    if (!missing.isEmpty()) {
      throw new BaseException(ErrorCode.REQUIRED_CONSENT_NOT_AGREED, missing);
    }
  }

  // 서버가 확인한 User를
  @Transactional
  public void recordConsents(User user, List<ConsentDecision> decisions) {
    UUID userId = persistedUserId(user);
    var indexed = validateDecisions(decisions);
    if (indexed.isEmpty()) {
      return;
    }

    User lockedUser = histories.lockUserForRecording(userId)
        .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
    histories.saveAll(indexed.values().stream()
        .map(decision -> new ConsentHistory(
            lockedUser, decision.consentType(), decision.documentVersion(), decision.agreed()))
        .toList());
  }

  @Transactional(readOnly = true)
  public ConsentStatusResponse getStatus(User user) {
    var consents = new EnumMap<ConsentType, Boolean>(ConsentType.class);

    // 1. 이력이 없으면 false (기본값)
    for (var type : ConsentType.values()) {
      consents.put(type, false);
    }

    // 2. 유형별 최신 이력을 조회
    for (var history : histories.findLatestByUserId(persistedUserId(user))) {
      ConsentType type = history.getConsentType();

      // 3. 최신 동의가 철회됐거나 문서 버전이 다르면 false
      consents.put(type, history.isAgreed()
          && history.getDocumentVersion().equals(ConsentPolicy.currentVersion(type)));
    }

    // 4. 최종 상태가 false인 모든 동의 유형을 추출
    var notAgreedTypes = consents.entrySet().stream()
        .filter(entry -> !entry.getValue())
        .map(Map.Entry::getKey)
        .toList();
    return new ConsentStatusResponse(
        notAgreedTypes.isEmpty(),
        ConsentPolicy.REQUIRED_AT_SIGNUP.stream().allMatch(consents::get),
        Collections.unmodifiableMap(consents),
        notAgreedTypes);
  }

  private Map<ConsentType, ConsentDecision> validateDecisions(List<ConsentDecision> decisions) {
    if (decisions == null) {
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }

    var indexed = new EnumMap<ConsentType, ConsentDecision>(ConsentType.class);
    for (var decision : decisions) {
      if (decision == null || decision.consentType() == null || decision.agreed() == null) {
        throw new BaseException(ErrorCode.INVALID_INPUT);
      }
      ConsentType type = decision.consentType();
      if (indexed.putIfAbsent(type, decision) != null) {
        throw new BaseException(ErrorCode.INVALID_INPUT, Map.of(type.name(), "동의 유형이 중복되었습니다."));
      }
      if (!ConsentPolicy.currentVersion(type).equals(decision.documentVersion())) {
        throw new BaseException(ErrorCode.INVALID_INPUT, Map.of(type.name(), "현재 문서 버전과 일치하지 않습니다."));
      }
    }
    return indexed;
  }

  private UUID persistedUserId(User user) {
    if (user == null || user.getId() == null) {
      throw new BaseException(ErrorCode.USER_NOT_FOUND);
    }
    return user.getId();
  }
}
