package com.dasigconnect.backend.model.dto.media;

import com.dasigconnect.backend.model.entity.MediaAsset;
import java.util.List;

public class AlbumPromptSuggestionAssetDto {

    private MediaAssetSummaryDto asset;
    private double score;
    private String confidence;
    private List<String> matchReasons;

    public static AlbumPromptSuggestionAssetDto from(MediaAsset asset, double score, String confidence,
                                                     List<String> matchReasons) {
        AlbumPromptSuggestionAssetDto dto = new AlbumPromptSuggestionAssetDto();
        dto.asset = MediaAssetSummaryDto.from(asset);
        dto.score = score;
        dto.confidence = confidence;
        dto.matchReasons = matchReasons;
        return dto;
    }

    public MediaAssetSummaryDto getAsset() { return asset; }
    public double getScore() { return score; }
    public String getConfidence() { return confidence; }
    public List<String> getMatchReasons() { return matchReasons; }
}
