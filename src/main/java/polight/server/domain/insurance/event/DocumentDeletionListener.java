package polight.server.domain.insurance.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import polight.server.domain.insurance.storage.PolicyDocumentStorage;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentDeletionListener {

  private final PolicyDocumentStorage storage;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void afterWithdrawal(DocumentDeletionRequestedEvent event) {
    for (String path : event.storedFilePaths()) {
      try {
        storage.delete(path);
      } catch (RuntimeException exception) {
        // The database is already committed; keep the path for manual cleanup.
        log.error("Withdrawal file deletion failed; manual cleanup required: {}", path, exception);
      }
    }
  }
}
