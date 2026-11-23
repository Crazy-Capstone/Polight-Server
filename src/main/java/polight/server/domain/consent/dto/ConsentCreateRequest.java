package polight.server.domain.consent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import polight.server.domain.consent.entity.ConsentType;

public record ConsentCreateRequest(
    @Schema(description = "7개 동의 유형의 선택값을 모두 전달합니다. false도 저장됩니다.",
        example = "{\"TERMS_OF_SERVICE\":true,\"PRIVACY_COLLECTION_USE\":true,\"OVERSEAS_TRANSFER\":true,\"AGE_14_PLUS\":true,\"POLICY_ANALYSIS\":true,\"THIRD_PARTY_POLICY\":false,\"HEALTH_INFORMATION\":true}")
    @NotNull @Size(min = 7, max = 7)
    Map<@NotNull ConsentType, @NotNull Boolean> consents) {}
