package polight.server.domain.insurance.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import polight.server.domain.insurance.config.FileStorageProperties;
import polight.server.global.exception.BusinessException;
import polight.server.global.exception.ErrorCode;

@Component
@RequiredArgsConstructor
public class LocalPolicyDocumentStorage implements PolicyDocumentStorage {
  private final FileStorageProperties properties;

  @Override
  public String store(MultipartFile file) {
    validate(file);
    try {
      Path directory = Path.of(properties.getDirectory()).toAbsolutePath().normalize();
      Files.createDirectories(directory);
      Path target = directory.resolve(UUID.randomUUID() + ".pdf").normalize();
      if (!target.startsWith(directory)) throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR);
      try (InputStream input = file.getInputStream()) {
        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
      }
      return target.toString();
    } catch (BusinessException exception) {
      throw exception;
    } catch (IOException exception) {
      throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR);
    }
  }

  public void validate(MultipartFile file) {
    if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.UNSUPPORTED_FILE, "빈 파일은 업로드할 수 없습니다.");
    if (file.getSize() > properties.getMaxSizeBytes()) throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
    String filename = file.getOriginalFilename();
    boolean extension = filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".pdf");
    boolean contentType = "application/pdf".equalsIgnoreCase(file.getContentType());
    if (!extension || !contentType || !hasPdfSignature(file)) {
      throw new BusinessException(ErrorCode.UNSUPPORTED_FILE);
    }
  }

  private boolean hasPdfSignature(MultipartFile file) {
    try (InputStream input = file.getInputStream()) {
      byte[] header = input.readNBytes(5);
      return header.length == 5 && header[0] == '%' && header[1] == 'P' && header[2] == 'D'
          && header[3] == 'F' && header[4] == '-';
    } catch (IOException exception) {
      throw new BusinessException(ErrorCode.UNSUPPORTED_FILE);
    }
  }
}
