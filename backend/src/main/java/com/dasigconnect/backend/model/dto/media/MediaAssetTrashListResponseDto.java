package com.dasigconnect.backend.model.dto.media;

import java.util.List;

public class MediaAssetTrashListResponseDto {

    private List<MediaAssetTrashDto> items;
    private int totalCount;
    private int page;
    private int pageSize;

    public MediaAssetTrashListResponseDto(List<MediaAssetTrashDto> items, int totalCount, int page, int pageSize) {
        this.items = items;
        this.totalCount = totalCount;
        this.page = page;
        this.pageSize = pageSize;
    }

    public List<MediaAssetTrashDto> getItems() {
        return items;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }
}
