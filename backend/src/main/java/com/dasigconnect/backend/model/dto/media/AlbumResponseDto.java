package com.dasigconnect.backend.model.dto.media;

import com.dasigconnect.backend.model.entity.MediaAlbum;
import java.time.Instant;
import java.util.UUID;

public class AlbumResponseDto {

    private UUID id;
    private String name;
    private String description;
    private String source;
    private UUID coverAssetId;
    private long assetCount;
    private Instant createdAt;
    private Instant updatedAt;

    public static AlbumResponseDto from(MediaAlbum album, long assetCount) {
        AlbumResponseDto dto = new AlbumResponseDto();
        dto.id = album.getId();
        dto.name = album.getName();
        dto.description = album.getDescription();
        dto.source = album.getSource();
        dto.coverAssetId = album.getCoverAsset() == null ? null : album.getCoverAsset().getId();
        dto.assetCount = assetCount;
        dto.createdAt = album.getCreatedAt();
        dto.updatedAt = album.getUpdatedAt();
        return dto;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getSource() { return source; }
    public UUID getCoverAssetId() { return coverAssetId; }
    public long getAssetCount() { return assetCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
