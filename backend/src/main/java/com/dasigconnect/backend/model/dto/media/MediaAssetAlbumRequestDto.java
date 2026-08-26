package com.dasigconnect.backend.model.dto.media;

import java.util.UUID;

public class MediaAssetAlbumRequestDto {

    private UUID albumId;

    public UUID getAlbumId() { return albumId; }
    public void setAlbumId(UUID albumId) { this.albumId = albumId; }
}
