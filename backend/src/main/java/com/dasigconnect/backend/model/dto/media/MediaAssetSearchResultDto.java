package com.dasigconnect.backend.model.dto.media;

import java.util.List;

public class MediaAssetSearchResultDto {

    private MediaAssetSummaryDto asset;
    private double score;
    private Integer lexicalRank;
    private Double lexicalScore;
    private Integer semanticRank;
    private Double semanticScore;
    private Integer imageRank;
    private Double imageScore;
    private List<String> matchReasons;

    public MediaAssetSearchResultDto(MediaAssetSummaryDto asset, double score, Integer lexicalRank,
                                     Double lexicalScore, List<String> matchReasons) {
        this(asset, score, lexicalRank, lexicalScore, null, null, null, null, matchReasons);
    }

    public MediaAssetSearchResultDto(MediaAssetSummaryDto asset, double score, Integer lexicalRank,
                                     Double lexicalScore, Integer semanticRank, Double semanticScore,
                                     Integer imageRank, Double imageScore, List<String> matchReasons) {
        this.asset = asset;
        this.score = score;
        this.lexicalRank = lexicalRank;
        this.lexicalScore = lexicalScore;
        this.semanticRank = semanticRank;
        this.semanticScore = semanticScore;
        this.imageRank = imageRank;
        this.imageScore = imageScore;
        this.matchReasons = matchReasons;
    }

    public MediaAssetSummaryDto getAsset() { return asset; }
    public double getScore() { return score; }
    public Integer getLexicalRank() { return lexicalRank; }
    public Double getLexicalScore() { return lexicalScore; }
    public Integer getSemanticRank() { return semanticRank; }
    public Double getSemanticScore() { return semanticScore; }
    public Integer getImageRank() { return imageRank; }
    public Double getImageScore() { return imageScore; }
    public List<String> getMatchReasons() { return matchReasons; }
}
