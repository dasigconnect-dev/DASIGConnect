package com.dasigconnect.backend.model.dto.media;

import java.util.UUID;

/** Set or clear an album cover. A null coverAssetId clears the cover. */
public class AlbumSetCoverRequestDto {

    private UUID coverAssetId;

    public UUID getCoverAssetId() { return coverAssetId; }
    public void setCoverAssetId(UUID coverAssetId) { this.coverAssetId = coverAssetId; }
}
