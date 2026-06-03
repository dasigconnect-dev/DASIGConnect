package com.dasigconnect.backend.model.dto.media;

import java.util.List;

public class MediaAssetSearchResponseDto {

    private String query;
    private List<MediaAssetSearchResultDto> items;
    private int totalCount;
    private int page;
    private int pageSize;
    /** True when results are ordered chronologically (date-only browse), not by relevance. */
    private boolean chronological;

    public MediaAssetSearchResponseDto(String query, List<MediaAssetSearchResultDto> items,
                                       int totalCount, int page, int pageSize) {
        this(query, items, totalCount, page, pageSize, false);
    }

    public MediaAssetSearchResponseDto(String query, List<MediaAssetSearchResultDto> items,
                                       int totalCount, int page, int pageSize, boolean chronological) {
        this.query = query;
        this.items = items;
        this.totalCount = totalCount;
        this.page = page;
        this.pageSize = pageSize;
        this.chronological = chronological;
    }

    public String getQuery() { return query; }
    public List<MediaAssetSearchResultDto> getItems() { return items; }
    public int getTotalCount() { return totalCount; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
    public boolean isChronological() { return chronological; }
}
