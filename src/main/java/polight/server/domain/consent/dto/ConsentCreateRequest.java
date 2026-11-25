package polight.server.domain.consent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;
import polight.server.domain.consent.entity.ConsentType;

public record ConsentCreateRequest(
    @Schema(description = "실제로 수집한 항목만 전달합니다. 생략 항목은 null로 저장하며 명시적 null과 빈 객체는 허용하지 않습니다.",
        example = "{\"TERMS_OF_SERVICE\":true,\"PRIVACY_COLLECTION_USE\":true,\"OVERSEAS_TRANSFER\":true,\"AGE_14_PLUS\":true}")
    @NotEmpty
    Map<@NotNull ConsentType, @NotNull Boolean> consents) {}
