package com.dasigconnect.backend.model.dto.systemhealth;

import java.util.UUID;

public record MediaEmbeddingCoverageDto(
        UUID institutionId,
        String institutionName,
        String assetStatus,
        long eligibleAssets,
        long imageEmbeddings,
        long semanticEmbeddings,
        double imageCoveragePercent,
        double semanticCoveragePercent,
        String imageModel,
        String imageProcessingVersion,
        String semanticModel,
        String semanticProcessingVersion) {
}
