package com.dasigconnect.backend.model.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * UC-4.6 feedback on a single media-search result. Not tied to a submission; the asset's
 * institution determines scope.
 */
public class SearchFeedbackRequestDto {

    /** The asset the feedback refers to. */
    @NotNull
    private UUID assetId;

    /** One of: thumbs_up | thumbs_down | selected | applied | dismissed. */
    @NotBlank
    @Size(max = 30)
    private String action;

    /** The search query that produced the result (context for analysis). */
    @Size(max = 500)
    private String query;

    /** 1-based rank of the asset in the result list, if known. */
    private Integer rank;

    public UUID getAssetId() { return assetId; }
    public void setAssetId(UUID assetId) { this.assetId = assetId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }
}
