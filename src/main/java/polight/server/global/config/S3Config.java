package polight.server.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3Config {

  /**
   * 자격증명은 {@link DefaultCredentialsProvider} 가 해결한다. 배포 환경(EC2)에서는 인스턴스 IAM 역할을 자동으로 사용하므로 액세스
   * 키를 환경변수로 주입할 필요가 없다.
   */
  @Bean
  public S3Client s3Client(@Value("${storage.s3.region}") String region) {
    return S3Client.builder()
        .region(Region.of(region))
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }

  @Bean
  public S3Presigner s3Presigner(@Value("${storage.s3.region}") String region) {
    return S3Presigner.builder()
        .region(Region.of(region))
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }
}
