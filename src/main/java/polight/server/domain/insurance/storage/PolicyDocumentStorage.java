package polight.server.domain.insurance.storage;

import org.springframework.web.multipart.MultipartFile;

public interface PolicyDocumentStorage {
  String store(MultipartFile file);
}
