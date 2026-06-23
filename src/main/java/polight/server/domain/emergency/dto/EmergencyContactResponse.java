package polight.server.domain.emergency.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import polight.server.domain.emergency.entity.EmergencyContactType;

@Schema(description = "긴급 연락처")
public record EmergencyContactResponse(UUID id, EmergencyContactType type, String name,
                                       String phone, String description, String insurerName) {}
