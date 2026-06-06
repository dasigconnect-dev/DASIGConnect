package com.dasigconnect.backend.model.dto.media;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * UC-4.12 Phase 7E: link an existing asset as a version/derivative of the path asset (the parent).
 * {@code relationType} is validated against the controlled vocabulary in the service.
 */
public class MediaAssetRelationCreateRequestDto {

    @NotNull
    private UUID childAssetId;

    @NotBlank
    private String relationType;

    public UUID getChildAssetId() { return childAssetId; }
    public void setChildAssetId(UUID childAssetId) { this.childAssetId = childAssetId; }
    public String getRelationType() { return relationType; }
    public void setRelationType(String relationType) { this.relationType = relationType; }
}
