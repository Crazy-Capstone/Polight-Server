package polight.server.domain.consent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import polight.server.domain.consent.entity.Consent;
import polight.server.domain.consent.entity.ConsentType;

public record ConsentResponse(
    UUID id,
    @JsonInclude(content = JsonInclude.Include.ALWAYS)
    @Schema(description = "항상 7개 항목을 반환합니다. true는 동의, false는 명시적 미동의, null은 미수집입니다.",
        example = "{\"TERMS_OF_SERVICE\":true,\"PRIVACY_COLLECTION_USE\":true,\"OVERSEAS_TRANSFER\":true,\"AGE_14_PLUS\":true,\"POLICY_ANALYSIS\":null,\"THIRD_PARTY_POLICY\":null,\"HEALTH_INFORMATION\":null}")
        Map<ConsentType, Boolean> consents,
    @Schema(description = "서버 최초 기록 시각") LocalDateTime createdAt,
    @Schema(description = "서버 최종 변경 시각") LocalDateTime updatedAt) {

  public static ConsentResponse from(Consent consent) {
    return new ConsentResponse(
        consent.getId(), consent.currentValues(), consent.getCreatedAt(), consent.getUpdatedAt());
  }
}
