package polight.server.domain.consent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import polight.server.domain.consent.entity.ConsentType;

public record ConsentUpdateRequest(
    @Schema(description = "수집/변경한 유형만 전달합니다. 생략 항목은 유지하고 명시적 null은 거부합니다. 실제 값이 변경되면 전체 수정 시각을 갱신합니다.",
        example = "{\"THIRD_PARTY_POLICY\":true}")
    @NotEmpty Map<@NotNull ConsentType, @NotNull Boolean> consents) {}
