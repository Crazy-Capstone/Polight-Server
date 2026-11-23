package polight.server.domain.consent.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "TERMS_OF_SERVICE: 이용약관 동의; PRIVACY_COLLECTION_USE: 개인정보 수집/이용 동의; "
    + "OVERSEAS_TRANSFER: 개인정보 국외이전 동의; AGE_14_PLUS: 만 14세 이상 확인; "
    + "POLICY_ANALYSIS: 증권분석을 위한 개인정보 수집/이용 동의; "
    + "THIRD_PARTY_POLICY: 다른 사람이 포함된 증권의 동의확인; HEALTH_INFORMATION: 건강정보 처리 동의")
public enum ConsentType {
  TERMS_OF_SERVICE,
  PRIVACY_COLLECTION_USE,
  OVERSEAS_TRANSFER,
  AGE_14_PLUS,
  POLICY_ANALYSIS,
  THIRD_PARTY_POLICY,
  HEALTH_INFORMATION
}
