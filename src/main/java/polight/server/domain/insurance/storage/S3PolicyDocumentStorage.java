package polight.server.domain.insurance.storage;

import java.io.IOException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3PolicyDocumentStorage implements PolicyDocumentStorage {

  private final S3Client s3Client;
  private final String bucket;
  private final String keyPrefix;

  public S3PolicyDocumentStorage(
      S3Client s3Client,
      @Value("${storage.s3.bucket}") String bucket,
      @Value("${storage.s3.key-prefix:policy-documents}") String keyPrefix) {
    if (bucket == null || bucket.isBlank()) {
      throw new IllegalStateException(
          "storage.type=s3 이면 storage.s3.bucket 이 필요합니다. S3_BUCKET 환경변수를 주입하세요.");
    }
    this.s3Client = s3Client;
    this.bucket = bucket;
    this.keyPrefix = keyPrefix.endsWith("/") ? keyPrefix : keyPrefix + "/";
  }

  @Override
  public String store(MultipartFile file) {
    String key = keyPrefix + UUID.randomUUID();

    PutObjectRequest.Builder request = PutObjectRequest.builder().bucket(bucket).key(key);
    if (file.getContentType() != null) {
      request.contentType(file.getContentType());
    }

    try {
      s3Client.putObject(
          request.build(), RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
    } catch (IOException | S3Exception exception) {
      throw new BaseException(ErrorCode.POLICY_DOCUMENT_STORAGE_FAILED, exception);
    }

    return key;
  }
}
