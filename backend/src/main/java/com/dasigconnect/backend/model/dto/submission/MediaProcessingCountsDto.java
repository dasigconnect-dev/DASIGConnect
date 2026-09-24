package com.dasigconnect.backend.model.dto.submission;

import com.dasigconnect.backend.model.dto.media.MediaAssetSummaryDto;
import java.util.List;

public record MediaProcessingCountsDto(int total, int ready, int processing, int failed) {

    public static MediaProcessingCountsDto from(List<MediaAssetSummaryDto> assets) {
        List<MediaAssetSummaryDto> safeAssets = assets == null ? List.of() : assets;
        int ready = 0;
        int processing = 0;
        int failed = 0;
        for (MediaAssetSummaryDto asset : safeAssets) {
            String status = asset.getStatus();
            if ("READY".equals(status)) ready++;
            else if ("FAILED".equals(status)) failed++;
            else if ("PROCESSING".equals(status) || "STAGED".equals(status)) processing++;
        }
        return new MediaProcessingCountsDto(safeAssets.size(), ready, processing, failed);
    }
}
