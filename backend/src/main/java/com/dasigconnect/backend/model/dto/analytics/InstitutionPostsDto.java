package com.dasigconnect.backend.model.dto.analytics;

import java.util.UUID;

public record InstitutionPostsDto(
        UUID institutionId,
        String institutionName,
        long totalPublished,
        long automatedPublished,
        long manualPublished,
        long adminDirectPosts) {
}
