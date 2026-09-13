package com.dasigconnect.backend.exception;

import java.util.List;

import com.dasigconnect.backend.model.dto.media.MediaAssetUsageDto;

public class MediaAssetDeletionConflictException extends RuntimeException {

    private final List<MediaAssetUsageDto> conflicts;

    public MediaAssetDeletionConflictException(String message, List<MediaAssetUsageDto> conflicts) {
        super(message);
        this.conflicts = List.copyOf(conflicts);
    }

    public List<MediaAssetUsageDto> getConflicts() {
        return conflicts;
    }
}
