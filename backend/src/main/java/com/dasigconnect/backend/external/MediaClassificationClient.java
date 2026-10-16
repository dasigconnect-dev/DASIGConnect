package com.dasigconnect.backend.external;

import com.dasigconnect.backend.model.dto.ai.MediaClassificationDto;
import java.util.List;

/** Provider-neutral contract for extracting structured metadata from media. */
public interface MediaClassificationClient {

    MediaClassificationDto classifyMedia(List<String> imageUrls);

    String modelName();
}
