package com.dasigconnect.backend.exception;

import java.util.Map;
import java.util.UUID;

public class MediaAssetDuplicateException extends RuntimeException {

    private final Map<String, Object> details;

    public MediaAssetDuplicateException(UUID assetId, String assetCode) {
        super("A matching asset already exists (" + assetCode + "). Choose the existing asset, upload anyway, or cancel.");
        this.details = Map.of("assetId", assetId, "assetCode", assetCode);
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
