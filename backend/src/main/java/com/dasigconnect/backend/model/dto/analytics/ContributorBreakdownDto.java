package com.dasigconnect.backend.model.dto.analytics;

import java.util.UUID;

public record ContributorBreakdownDto(
        UUID contributorId,
        String contributorName,
        long postsSubmitted,
        long postsPublished,
        long needsRevisionCount,
        long revisionCycles,
        double completenessRate,
        double averagePostingDelayDays) {
}
