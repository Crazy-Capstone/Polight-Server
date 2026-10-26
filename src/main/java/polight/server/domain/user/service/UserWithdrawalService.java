package polight.server.domain.user.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.auth.service.RefreshTokenStore;
import polight.server.domain.insurance.event.DocumentDeletionRequestedEvent;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.insurance.repository.PolicyDocumentRepository;
import polight.server.domain.user.repository.UserRepository;
import polight.server.domain.user.repository.UserWithdrawalRepository;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class UserWithdrawalService {

  private final UserRepository userRepository;
  private final UserWithdrawalRepository userWithdrawalRepository;
  private final PolicyDocumentRepository policyDocumentRepository;
  private final RefreshTokenStore refreshTokenStore;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public void withdraw(UUID userId) {
    if (userId == null) {
      throw new BaseException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
    userRepository.findByIdForWithdrawal(userId)
        .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    var filePaths = policyDocumentRepository.findByUserId(userId).stream()
        .map(PolicyDocument::getStoredFilePath)
        .distinct()
        .toList();
    // Redis failure aborts withdrawal; already revoked sessions are harmless on rollback.
    refreshTokenStore.revokeAll(userId);
    userWithdrawalRepository.deleteUserData(userId);
    eventPublisher.publishEvent(new DocumentDeletionRequestedEvent(filePaths));
  }
}
