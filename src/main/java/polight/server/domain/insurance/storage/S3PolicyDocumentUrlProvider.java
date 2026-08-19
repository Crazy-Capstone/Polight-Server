package polight.server.domain.insurance.storage;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/** Private S3 객체를 AI 서버가 일시적으로 읽을 수 있는 Presigned GET URL로 변환한다. */
@Component
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3PolicyDocumentUrlProvider implements PolicyDocumentUrlProvider {

  private final S3Presigner presigner;
  private final String bucket;
  private final Duration expiry;

  public S3PolicyDocumentUrlProvider(
      S3Presigner presigner,
      @Value("${storage.s3.bucket}") String bucket,
      @Value("${storage.s3.presigned-get-expiry:10m}") Duration expiry) {
    this.presigner = presigner;
    this.bucket = bucket;
    this.expiry = expiry;
  }

  @Override
  public URI createDownloadUrl(String objectKey) {
    if (objectKey == null || objectKey.isBlank()) {
      throw new BaseException(ErrorCode.POLICY_DOCUMENT_URL_GENERATION_FAILED);
    }

    try {
      GetObjectRequest getObjectRequest =
          GetObjectRequest.builder().bucket(bucket).key(objectKey).build();
      GetObjectPresignRequest presignRequest =
          GetObjectPresignRequest.builder()
              .signatureDuration(expiry)
              .getObjectRequest(getObjectRequest)
              .build();
      return URI.create(presigner.presignGetObject(presignRequest).url().toString());
    } catch (RuntimeException exception) {
      throw new BaseException(ErrorCode.POLICY_DOCUMENT_URL_GENERATION_FAILED, exception);
    }
  }
}
