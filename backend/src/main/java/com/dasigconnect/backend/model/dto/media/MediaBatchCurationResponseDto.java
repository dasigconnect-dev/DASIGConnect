package com.dasigconnect.backend.model.dto.media;

public class MediaBatchCurationResponseDto {

    private int curatedCount;

    public MediaBatchCurationResponseDto(int curatedCount) {
        this.curatedCount = curatedCount;
    }

    public int getCuratedCount() { return curatedCount; }
}
