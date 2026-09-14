package com.dasigconnect.backend.model.dto.institution;

import java.util.UUID;

public record InstitutionCountSummaryDto(
        UUID institutionId,
        long contributors,
        long moderators,
        long pendingInvitations) {
}
