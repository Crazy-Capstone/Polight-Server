package polight.server.domain.consent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import polight.server.domain.consent.entity.Consent;
import polight.server.domain.consent.entity.ConsentType;

public record ConsentResponse(
    UUID id,
    @Schema(description = "7개 동의 유형의 현재 선택값",
        example = "{\"TERMS_OF_SERVICE\":true,\"PRIVACY_COLLECTION_USE\":true,\"OVERSEAS_TRANSFER\":true,\"AGE_14_PLUS\":true,\"POLICY_ANALYSIS\":true,\"THIRD_PARTY_POLICY\":false,\"HEALTH_INFORMATION\":true}")
        Map<ConsentType, Boolean> consents,
    @Schema(description = "서버 최초 기록 시각") LocalDateTime createdAt,
    @Schema(description = "서버 최종 변경 시각") LocalDateTime updatedAt) {

  public static ConsentResponse from(Consent consent) {
    return new ConsentResponse(
        consent.getId(), consent.currentValues(), consent.getCreatedAt(), consent.getUpdatedAt());
  }
}
