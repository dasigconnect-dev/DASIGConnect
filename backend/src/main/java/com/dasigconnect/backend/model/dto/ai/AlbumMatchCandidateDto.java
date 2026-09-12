package com.dasigconnect.backend.model.dto.ai;

import java.util.List;
import java.util.UUID;

public class AlbumMatchCandidateDto {

    private UUID albumId;
    private String albumName;
    private double score;
    private List<String> reasons;

    public static AlbumMatchCandidateDto of(UUID albumId, String albumName, double score, List<String> reasons) {
        AlbumMatchCandidateDto dto = new AlbumMatchCandidateDto();
        dto.albumId = albumId;
        dto.albumName = albumName;
        dto.score = score;
        dto.reasons = reasons == null ? List.of() : List.copyOf(reasons);
        return dto;
    }

    public UUID getAlbumId() { return albumId; }
    public String getAlbumName() { return albumName; }
    public double getScore() { return score; }
    public List<String> getReasons() { return reasons; }
}
