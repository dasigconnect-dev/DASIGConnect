package com.dasigconnect.backend.model.dto.media;

import com.dasigconnect.backend.model.entity.MediaAlbum;
import com.dasigconnect.backend.model.entity.MediaAsset;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Album with its ordered assets (GET by id). */
public class AlbumDetailDto {

    private UUID id;
    private String name;
    private String description;
    private String source;
    private UUID coverAssetId;
    private int assetCount;
    private Instant createdAt;
    private Instant updatedAt;
    private List<MediaAssetSummaryDto> assets;

    public static AlbumDetailDto from(MediaAlbum album, List<MediaAsset> assets) {
        AlbumDetailDto dto = new AlbumDetailDto();
        dto.id = album.getId();
        dto.name = album.getName();
        dto.description = album.getDescription();
        dto.source = album.getSource();
        dto.coverAssetId = album.getCoverAsset() == null ? null : album.getCoverAsset().getId();
        dto.assets = assets.stream().map(MediaAssetSummaryDto::from).toList();
        dto.assetCount = dto.assets.size();
        dto.createdAt = album.getCreatedAt();
        dto.updatedAt = album.getUpdatedAt();
        return dto;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getSource() { return source; }
    public UUID getCoverAssetId() { return coverAssetId; }
    public int getAssetCount() { return assetCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<MediaAssetSummaryDto> getAssets() { return assets; }
}
