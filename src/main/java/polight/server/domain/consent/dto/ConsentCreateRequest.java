package polight.server.domain.consent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;
import polight.server.domain.consent.entity.ConsentType;

public record ConsentCreateRequest(
    @Schema(description = "수집한 항목만 전달합니다. 최초 저장 시 생략 항목은 null이며, 이후 POST는 전달 항목만 갱신합니다. 명시적 null과 빈 객체는 허용하지 않습니다.",
        example = "{\"TERMS_OF_SERVICE\":true,\"PRIVACY_COLLECTION_USE\":true,\"OVERSEAS_TRANSFER\":true,\"AGE_14_PLUS\":true}")
    @NotEmpty
    Map<@NotNull ConsentType, @NotNull Boolean> consents) {}
