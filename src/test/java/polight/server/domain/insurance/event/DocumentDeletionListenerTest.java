package polight.server.domain.insurance.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import polight.server.domain.insurance.storage.PolicyDocumentStorage;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class DocumentDeletionListenerTest {

  @Mock private PolicyDocumentStorage storage;
  @InjectMocks private DocumentDeletionListener listener;

  @Test
  void deletesEachFileWithoutPersistenceOrRetry() {
    listener.deleteDocuments(new DocumentDeletionRequestedEvent(List.of("first", "second")));

    verify(storage).delete("first");
    verify(storage).delete("second");
    verifyNoMoreInteractions(storage);
  }

  @Test
  void continuesAfterFileDeletionFailure(CapturedOutput output) {
    doThrow(new IllegalStateException("Storage unavailable")).when(storage).delete("failed");

    listener.deleteDocuments(new DocumentDeletionRequestedEvent(List.of("failed", "success")));

    verify(storage).delete("failed");
    verify(storage).delete("success");
    verifyNoMoreInteractions(storage);
    assertThat(output).contains("Document file deletion failed; manual cleanup required: failed");
  }

  @Test
  void eventKeepsAnIndependentCopyOfPaths() {
    var paths = new ArrayList<>(List.of("file"));
    var event = new DocumentDeletionRequestedEvent(paths);
    paths.clear();
    assertThat(event.storedFilePaths()).containsExactly("file");
  }
}
