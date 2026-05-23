package com.dasigconnect.backend.model.dto.media;

import java.util.List;

public class MediaAssetListResponseDto {

    private List<MediaAssetSummaryDto> items;
    private int totalCount;
    private int page;
    private int pageSize;

    public MediaAssetListResponseDto(List<MediaAssetSummaryDto> items, int totalCount, int page, int pageSize) {
        this.items = items;
        this.totalCount = totalCount;
        this.page = page;
        this.pageSize = pageSize;
    }

    public List<MediaAssetSummaryDto> getItems() {
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
