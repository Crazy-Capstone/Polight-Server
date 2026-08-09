package polight.server.domain.insurance.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class LocalPolicyDocumentStorageTest {

  @TempDir Path tempDir;

  @Test
  void store_writesFileUnderConfiguredDirectory() throws IOException {
    Path directory = tempDir.resolve("policy-documents");
    LocalPolicyDocumentStorage storage = new LocalPolicyDocumentStorage(directory.toString());
    MockMultipartFile file =
        new MockMultipartFile("file", "약관.pdf", "application/pdf", "policy-bytes".getBytes());

    String storedFilePath = storage.store(file);

    assertThat(Path.of(storedFilePath))
        .isAbsolute()
        .hasParent(directory)
        .hasBinaryContent("policy-bytes".getBytes());
  }

  @Test
  void store_doesNotReuseTheSamePathForTwoUploads() {
    LocalPolicyDocumentStorage storage = new LocalPolicyDocumentStorage(tempDir.toString());
    MockMultipartFile file = new MockMultipartFile("file", "약관.pdf", "application/pdf", "a".getBytes());

    assertThat(storage.store(file)).isNotEqualTo(storage.store(file));
  }

  @Test
  void store_createsMissingDirectories() {
    Path nested = tempDir.resolve("a/b/c");
    LocalPolicyDocumentStorage storage = new LocalPolicyDocumentStorage(nested.toString());

    String storedFilePath = storage.store(new MockMultipartFile("file", "x.pdf", null, "x".getBytes()));

    assertThat(Files.exists(Path.of(storedFilePath))).isTrue();
  }
}
