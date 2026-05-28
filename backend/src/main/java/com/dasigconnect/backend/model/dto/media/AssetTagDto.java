package com.dasigconnect.backend.model.dto.media;

import java.time.Instant;
import java.util.UUID;

import com.dasigconnect.backend.model.entity.AssetTag;

public record AssetTagDto(UUID id, String label, String source, Instant createdAt) {

    public static AssetTagDto from(AssetTag tag) {
        return new AssetTagDto(tag.getId(), tag.getLabel(), tag.getSource(), tag.getCreatedAt());
    }
}
