package com.dasigconnect.backend.model.dto.media;

import com.dasigconnect.backend.model.entity.MediaAlbum;
import java.time.Instant;
import java.util.UUID;

public record MediaAlbumDto(
        UUID id,
        UUID institutionId,
        String institutionCode,
        String institutionName,
        UUID parentAlbumId,
        String name,
        long childAlbumCount,
        long assetCount,
        boolean canDelete,
        boolean shared,
        Instant createdAt,
        Instant updatedAt
) {
    public static MediaAlbumDto from(MediaAlbum album) {
        return from(album, 0L, 0L, false);
    }

    public static MediaAlbumDto from(MediaAlbum album, long childAlbumCount, long assetCount) {
        return from(album, childAlbumCount, assetCount, false);
    }

    public static MediaAlbumDto from(MediaAlbum album, long childAlbumCount, long assetCount, boolean canDelete) {
        return new MediaAlbumDto(
                album.getId(),
                album.getInstitution().getId(),
                album.getInstitution().getCode(),
                album.getInstitution().getName(),
                album.getParentAlbum() == null ? null : album.getParentAlbum().getId(),
                album.getName(),
                childAlbumCount,
                assetCount,
                canDelete,
                album.getInstitution().isProtected(),
                album.getCreatedAt(),
                album.getUpdatedAt());
    }
}
