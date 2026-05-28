package com.dasigconnect.backend.model.dto.analytics;

import java.util.UUID;

public record InstitutionFilterOptionDto(
        UUID institutionId,
        String institutionName) {
}
