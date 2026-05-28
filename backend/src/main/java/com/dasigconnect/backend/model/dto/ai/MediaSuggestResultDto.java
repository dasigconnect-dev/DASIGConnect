package com.dasigconnect.backend.model.dto.ai;

import com.dasigconnect.backend.model.entity.MediaAsset;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class MediaSuggestResultDto {

    private UUID id;
    private String assetCode;
    private String storageUrl;
    private String fileName;
    private String fileType;
    private long fileSizeBytes;
    private String aiCategory;
    private double similarityScore;
    private List<String> matchReasons;
    private Instant createdAt;

    public static MediaSuggestResultDto from(MediaAsset asset, double score) {
        return from(asset, score, List.of());
    }

    public static MediaSuggestResultDto from(MediaAsset asset, double score, List<String> matchReasons) {
        MediaSuggestResultDto dto = new MediaSuggestResultDto();
        dto.id = asset.getId();
        dto.assetCode = asset.getAssetCode();
        dto.storageUrl = asset.getStorageUrl();
        dto.fileName = asset.getFileName();
        dto.fileType = asset.getFileType().name();
        dto.fileSizeBytes = asset.getFileSizeBytes();
        dto.aiCategory = asset.getAiCategory();
        dto.similarityScore = score;
        dto.matchReasons = matchReasons == null ? List.of() : List.copyOf(matchReasons);
        dto.createdAt = asset.getCreatedAt();
        return dto;
    }

    public UUID getId() { return id; }
    public String getAssetCode() { return assetCode; }
    public String getStorageUrl() { return storageUrl; }
    public String getFileName() { return fileName; }
    public String getFileType() { return fileType; }
    public long getFileSizeBytes() { return fileSizeBytes; }
    public String getAiCategory() { return aiCategory; }
    public double getSimilarityScore() { return similarityScore; }
    public List<String> getMatchReasons() { return matchReasons; }
    public Instant getCreatedAt() { return createdAt; }
}
