package polight.server.domain.consent.service;

import java.util.Map;
import org.springframework.stereotype.Component;
import polight.server.domain.consent.entity.ConsentType;

@Component
public class ConsentDocumentPolicy {

  private static final String DEVELOPMENT_VERSION = "dev-v1";
  private static final Map<ConsentType, String> CURRENT_VERSIONS = Map.of(
      ConsentType.TERMS_OF_SERVICE, DEVELOPMENT_VERSION,
      ConsentType.PRIVACY_COLLECTION_USE, DEVELOPMENT_VERSION,
      ConsentType.OVERSEAS_TRANSFER, DEVELOPMENT_VERSION,
      ConsentType.AGE_14_PLUS, DEVELOPMENT_VERSION,
      ConsentType.POLICY_ANALYSIS, DEVELOPMENT_VERSION,
      ConsentType.THIRD_PARTY_POLICY, DEVELOPMENT_VERSION,
      ConsentType.HEALTH_INFORMATION, DEVELOPMENT_VERSION);

  public String currentVersion(ConsentType type) {
    return CURRENT_VERSIONS.get(type);
  }
}
