package polight.server.domain.insurance.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import polight.server.global.exception.BaseException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3PolicyDocumentUrlProviderTest {

  private S3Presigner presigner;
  private S3PolicyDocumentUrlProvider provider;

  @BeforeEach
  void setUp() {
    presigner =
        S3Presigner.builder()
            .region(Region.AP_NORTHEAST_2)
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create("access", "secret")))
            .build();
    provider = new S3PolicyDocumentUrlProvider(presigner, "private-bucket", Duration.ofMinutes(10));
  }

  @AfterEach
  void tearDown() {
    presigner.close();
  }

  @Test
  void createsTemporaryGetUrlFromObjectKey() {
    URI url = provider.createDownloadUrl("policy-documents/document-id");

    assertThat(url.getScheme()).isEqualTo("https");
    assertThat(url.getHost()).contains("private-bucket").contains("s3");
    assertThat(url.getPath()).isEqualTo("/policy-documents/document-id");
    assertThat(url.getQuery()).contains("X-Amz-Signature").contains("X-Amz-Expires=600");
  }

  @Test
  void rejectsBlankObjectKey() {
    assertThatThrownBy(() -> provider.createDownloadUrl(" "))
        .isInstanceOf(BaseException.class)
        .hasMessageContaining("다운로드 URL");
  }
}
