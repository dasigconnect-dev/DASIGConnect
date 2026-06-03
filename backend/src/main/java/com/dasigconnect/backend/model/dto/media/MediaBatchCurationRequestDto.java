package com.dasigconnect.backend.model.dto.media;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

public class MediaBatchCurationRequestDto {

    @Valid
    @Size(max = 250)
    private List<MediaAssetCurationEditDto> edits;

    public List<MediaAssetCurationEditDto> getEdits() { return edits; }
    public void setEdits(List<MediaAssetCurationEditDto> edits) { this.edits = edits; }
}
