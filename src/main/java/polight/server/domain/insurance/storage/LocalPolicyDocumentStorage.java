package polight.server.domain.insurance.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@Component
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalPolicyDocumentStorage implements PolicyDocumentStorage {

  private final Path directory;

  public LocalPolicyDocumentStorage(
      @Value("${storage.policy-documents-directory:uploads/policy-documents}") String directory) {
    this.directory = Path.of(directory).toAbsolutePath().normalize();
  }

  @Override
  public String store(MultipartFile file) {
    Path target = directory.resolve(UUID.randomUUID().toString());

    try {
      Files.createDirectories(target.getParent());
      Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException exception) {
      throw new BaseException(ErrorCode.POLICY_DOCUMENT_STORAGE_FAILED, exception);
    }

    return target.toString();
  }
}
