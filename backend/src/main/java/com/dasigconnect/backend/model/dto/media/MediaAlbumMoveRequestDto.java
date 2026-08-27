package com.dasigconnect.backend.model.dto.media;

import java.util.UUID;

/** Body for PATCH /media-assets/albums/{id}/parent — {@code parentAlbumId} null moves to the root. */
public class MediaAlbumMoveRequestDto {

    private UUID parentAlbumId;

    /** Optional — move the folder to this institution's root (e.g. the shared default). */
    private UUID institutionId;

    public UUID getParentAlbumId() { return parentAlbumId; }
    public void setParentAlbumId(UUID parentAlbumId) { this.parentAlbumId = parentAlbumId; }

    public UUID getInstitutionId() { return institutionId; }
    public void setInstitutionId(UUID institutionId) { this.institutionId = institutionId; }
}
