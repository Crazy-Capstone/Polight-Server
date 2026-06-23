package polight.server.domain.insurance.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import polight.server.domain.insurance.config.FileStorageProperties;
import polight.server.global.exception.BusinessException;

class LocalPolicyDocumentStorageTest {
  @TempDir Path tempDir;

  @Test
  void upload_acceptsValidPdfAndUsesGeneratedFilename() {
    LocalPolicyDocumentStorage storage = storage(1024);
    var file = new MockMultipartFile("file", "policy.pdf", "application/pdf", "%PDF-1.7 body".getBytes());

    String stored = storage.store(file);

    assertThat(Path.of(stored).getFileName().toString()).endsWith(".pdf").doesNotContain("policy");
  }

  @Test
  void upload_rejectsInvalidMimeExtensionOrSignature() {
    LocalPolicyDocumentStorage storage = storage(1024);
    var file = new MockMultipartFile("file", "policy.pdf", "text/plain", "not-pdf".getBytes());

    assertThatThrownBy(() -> storage.store(file)).isInstanceOf(BusinessException.class);
  }

  private LocalPolicyDocumentStorage storage(long maxSize) {
    FileStorageProperties properties = new FileStorageProperties();
    properties.setDirectory(tempDir.toString());
    properties.setMaxSizeBytes(maxSize);
    return new LocalPolicyDocumentStorage(properties);
  }
}
