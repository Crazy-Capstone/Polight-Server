package polight.server.domain.insurance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "polight.file-storage")
public class FileStorageProperties {
  private String directory = "./data/policy-documents";
  private long maxSizeBytes = 10 * 1024 * 1024;

  public String getDirectory() { return directory; }
  public void setDirectory(String directory) { this.directory = directory; }
  public long getMaxSizeBytes() { return maxSizeBytes; }
  public void setMaxSizeBytes(long maxSizeBytes) { this.maxSizeBytes = maxSizeBytes; }
}
