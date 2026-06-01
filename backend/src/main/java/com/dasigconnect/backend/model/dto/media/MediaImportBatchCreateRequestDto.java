package com.dasigconnect.backend.model.dto.media;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;

public class MediaImportBatchCreateRequestDto {

    @Min(1)
    @Max(500)
    private int assetCount;

    /** Required for administrators; ignored for institution-scoped users. */
    private UUID institutionId;

    public int getAssetCount() { return assetCount; }
    public void setAssetCount(int assetCount) { this.assetCount = assetCount; }

    public UUID getInstitutionId() { return institutionId; }
    public void setInstitutionId(UUID institutionId) { this.institutionId = institutionId; }
}
