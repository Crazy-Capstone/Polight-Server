package polight.server.domain.consent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import polight.server.domain.consent.entity.ConsentType;

public record ConsentStatusResponse(
    @Schema(description = "전체 동의 유형에 현재 유효한 동의가 있는지") boolean allAgreed,
    @Schema(description = "회원가입 필수 유형에 모두 현재 유효한 동의가 있는지") boolean allRequiredAgreed,
    @Schema(description = "모든 동의 유형의 현재 유효한 동의 여부") Map<ConsentType, Boolean> consents,
    @Schema(description = "현재 유효한 동의가 없는 유형의 목록") List<ConsentType> notAgreedTypes) {}
