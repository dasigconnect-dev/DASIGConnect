package com.dasigconnect.backend.repository;

import java.util.UUID;

public record MediaAssetSearchHit(UUID assetId, double lexicalScore, int lexicalRank) {
}
