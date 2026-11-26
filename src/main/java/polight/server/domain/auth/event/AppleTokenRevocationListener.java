package polight.server.domain.auth.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import polight.server.domain.auth.client.AppleOAuthClient;
import polight.server.domain.auth.service.AppleTokenCipher;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppleTokenRevocationListener {

  private final AppleOAuthClient client;
  private final AppleTokenCipher cipher;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void revoke(AppleTokenRevocationRequestedEvent event) {
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        client.revoke(cipher.decrypt(event.encryptedRefreshToken(), event.subject()));
        return;
      } catch (BaseException exception) {
        ErrorCode code = exception.getErrorCode();
        if (attempt == 0 && (code == ErrorCode.APPLE_SERVICE_UNAVAILABLE
            || code == ErrorCode.APPLE_TOKEN_REVOCATION_FAILED)) {
          try {
            Thread.sleep(200);
            continue;
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
          }
        }
        log.error("Apple token revocation did not complete after account deletion: userId={}, code={}", event.userId(), code);
        return;
      } catch (RuntimeException exception) {
        log.error("Apple token revocation did not complete after account deletion: userId={}", event.userId());
        return;
      }
    }
  }
}
