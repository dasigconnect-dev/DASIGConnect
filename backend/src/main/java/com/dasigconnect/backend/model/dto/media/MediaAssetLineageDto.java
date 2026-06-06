package com.dasigconnect.backend.model.dto.media;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * UC-4.12 Phase 7E: lineage around one asset. {@code parents} are what this asset is a
 * version/derivative of; {@code children} are this asset's own versions and derivatives.
 */
public record MediaAssetLineageDto(
        UUID assetId,
        List<Edge> parents,
        List<Edge> children) {

    /** A single lineage edge plus a summary of the asset on the other end. */
    public record Edge(
            UUID relationId,
            String relationType,
            Instant createdAt,
            MediaAssetSummaryDto asset) {
    }
}
