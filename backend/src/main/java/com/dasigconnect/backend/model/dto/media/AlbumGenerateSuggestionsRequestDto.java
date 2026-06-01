package com.dasigconnect.backend.model.dto.media;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class AlbumGenerateSuggestionsRequestDto {

    @NotNull
    private UUID importBatchId;

    /** Required for administrators; ignored for institution-scoped users. */
    private UUID institutionId;

    @Min(2)
    @Max(20)
    private int minGroupSize = 2;

    public UUID getImportBatchId() { return importBatchId; }
    public void setImportBatchId(UUID importBatchId) { this.importBatchId = importBatchId; }

    public UUID getInstitutionId() { return institutionId; }
    public void setInstitutionId(UUID institutionId) { this.institutionId = institutionId; }

    public int getMinGroupSize() { return minGroupSize; }
    public void setMinGroupSize(int minGroupSize) { this.minGroupSize = minGroupSize; }
}
