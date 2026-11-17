package polight.server.domain.consent.service;

import static polight.server.domain.consent.entity.ConsentType.AGE_14_PLUS;
import static polight.server.domain.consent.entity.ConsentType.HEALTH_INFORMATION;
import static polight.server.domain.consent.entity.ConsentType.OVERSEAS_TRANSFER;
import static polight.server.domain.consent.entity.ConsentType.POLICY_ANALYSIS;
import static polight.server.domain.consent.entity.ConsentType.PRIVACY_COLLECTION_USE;
import static polight.server.domain.consent.entity.ConsentType.TERMS_OF_SERVICE;
import static polight.server.domain.consent.entity.ConsentType.THIRD_PARTY_POLICY;

import java.util.List;
import java.util.Map;
import polight.server.domain.consent.entity.ConsentType;

final class ConsentPolicy {

  static final List<ConsentType> REQUIRED_AT_SIGNUP =
      List.of(TERMS_OF_SERVICE, PRIVACY_COLLECTION_USE, AGE_14_PLUS);

  private static final String DEVELOPMENT_VERSION = "dev-v1";

  private static final Map<ConsentType, String> CURRENT_VERSIONS = Map.of(
      TERMS_OF_SERVICE, DEVELOPMENT_VERSION,
      PRIVACY_COLLECTION_USE, DEVELOPMENT_VERSION,
      OVERSEAS_TRANSFER, DEVELOPMENT_VERSION,
      AGE_14_PLUS, DEVELOPMENT_VERSION,
      POLICY_ANALYSIS, DEVELOPMENT_VERSION,
      THIRD_PARTY_POLICY, DEVELOPMENT_VERSION,
      HEALTH_INFORMATION, DEVELOPMENT_VERSION);

  private ConsentPolicy() {}

  static String currentVersion(ConsentType type) {
    return CURRENT_VERSIONS.get(type);
  }
}
