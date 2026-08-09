package polight.server.domain.insurance.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import polight.server.global.exception.BaseException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class S3PolicyDocumentStorageTest {

  @Mock private S3Client s3Client;

  @Test
  void store_putsObjectUnderKeyPrefixAndReturnsTheKey() {
    S3PolicyDocumentStorage storage =
        new S3PolicyDocumentStorage(s3Client, "polight-bucket", "policy-documents");
    MockMultipartFile file =
        new MockMultipartFile("file", "약관.pdf", "application/pdf", "policy-bytes".getBytes());

    String key = storage.store(file);

    ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3Client).putObject(request.capture(), any(RequestBody.class));
    assertThat(request.getValue().bucket()).isEqualTo("polight-bucket");
    assertThat(request.getValue().key()).isEqualTo(key).startsWith("policy-documents/");
    assertThat(request.getValue().contentType()).isEqualTo("application/pdf");
  }

  @Test
  void store_normalizesKeyPrefixWithTrailingSlash() {
    S3PolicyDocumentStorage storage =
        new S3PolicyDocumentStorage(s3Client, "polight-bucket", "policy-documents/");

    assertThat(storage.store(new MockMultipartFile("file", "x.pdf", null, "x".getBytes())))
        .startsWith("policy-documents/")
        .doesNotContain("//");
  }

  @Test
  void store_translatesS3FailureIntoServerError() {
    given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .willThrow(S3Exception.builder().message("access denied").build());
    S3PolicyDocumentStorage storage =
        new S3PolicyDocumentStorage(s3Client, "polight-bucket", "policy-documents");

    assertThatThrownBy(
            () -> storage.store(new MockMultipartFile("file", "x.pdf", null, "x".getBytes())))
        .isInstanceOf(BaseException.class)
        .hasMessageContaining("약관 파일을 저장하지 못했습니다");
  }

  @Test
  void constructor_rejectsMissingBucket() {
    assertThatThrownBy(() -> new S3PolicyDocumentStorage(s3Client, "  ", "policy-documents"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("S3_BUCKET");
  }

  @Test
  void store_translatesFileReadFailureIntoServerError() throws IOException {
    S3PolicyDocumentStorage storage =
        new S3PolicyDocumentStorage(s3Client, "polight-bucket", "policy-documents");
    MockMultipartFile unreadable =
        new MockMultipartFile("file", "x.pdf", null, "x".getBytes()) {
          @Override
          public java.io.InputStream getInputStream() throws IOException {
            throw new IOException("boom");
          }
        };

    assertThatThrownBy(() -> storage.store(unreadable))
        .isInstanceOf(BaseException.class)
        .hasMessageContaining("약관 파일을 저장하지 못했습니다");
  }
}
