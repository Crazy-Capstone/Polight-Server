package polight.server.domain.consent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import polight.server.domain.consent.entity.ConsentType;

public record ConsentUpdateRequest(
    @Schema(description = "변경할 유형만 전달합니다. 누락된 유형은 유지하며 null은 허용하지 않습니다.",
        example = "{\"THIRD_PARTY_POLICY\":true}")
    @NotEmpty Map<@NotNull ConsentType, @NotNull Boolean> consents) {}
