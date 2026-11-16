package polight.server.domain.consent.dto;

import polight.server.domain.consent.entity.ConsentType;

public record ConsentDecision(ConsentType consentType, String documentVersion, Boolean agreed) {}
