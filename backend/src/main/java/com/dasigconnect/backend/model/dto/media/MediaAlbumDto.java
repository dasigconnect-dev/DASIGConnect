package com.dasigconnect.backend.model.dto.media;

import com.dasigconnect.backend.model.entity.MediaAlbum;
import java.time.Instant;
import java.util.UUID;

public record MediaAlbumDto(
        UUID id,
        UUID institutionId,
        String name,
        Instant createdAt,
        Instant updatedAt
) {
    public static MediaAlbumDto from(MediaAlbum album) {
        return new MediaAlbumDto(
                album.getId(),
                album.getInstitution().getId(),
                album.getName(),
                album.getCreatedAt(),
                album.getUpdatedAt());
    }
}
