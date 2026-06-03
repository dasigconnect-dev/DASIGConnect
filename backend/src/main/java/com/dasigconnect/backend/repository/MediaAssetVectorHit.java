package com.dasigconnect.backend.repository;

import java.util.UUID;

public record MediaAssetVectorHit(UUID assetId, double vectorScore, int rank) {

    public static MediaAssetVectorHit from(Object[] row, int rank) {
        return new MediaAssetVectorHit(
                UUID.fromString((String) row[0]),
                ((Number) row[1]).doubleValue(),
                rank);
    }
}
