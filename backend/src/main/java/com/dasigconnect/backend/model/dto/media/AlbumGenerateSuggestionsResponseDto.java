package com.dasigconnect.backend.model.dto.media;

import java.util.List;
import java.util.UUID;

public class AlbumGenerateSuggestionsResponseDto {

    private UUID importBatchId;
    private int groupsEvaluated;
    private int albumsCreated;
    private List<AlbumResponseDto> albums;

    public AlbumGenerateSuggestionsResponseDto(
            UUID importBatchId,
            int groupsEvaluated,
            List<AlbumResponseDto> albums) {
        this.importBatchId = importBatchId;
        this.groupsEvaluated = groupsEvaluated;
        this.albums = albums;
        this.albumsCreated = albums.size();
    }

    public UUID getImportBatchId() { return importBatchId; }
    public int getGroupsEvaluated() { return groupsEvaluated; }
    public int getAlbumsCreated() { return albumsCreated; }
    public List<AlbumResponseDto> getAlbums() { return albums; }
}
