package polight.server.domain.consent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import polight.server.domain.consent.entity.ConsentType;

public record ConsentDecision(
    @NotNull ConsentType consentType,
    @Schema(example = "dev-v1") @NotBlank @Size(max = 30) String documentVersion,
    @Schema(description = "동의는 true, 철회는 false") @NotNull Boolean agreed) {}
