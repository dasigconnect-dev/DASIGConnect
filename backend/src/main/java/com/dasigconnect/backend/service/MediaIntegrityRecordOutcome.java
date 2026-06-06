package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.media.MediaIntegrityResultDto;
import com.dasigconnect.backend.model.entity.MediaIntegrityStatus;

public record MediaIntegrityRecordOutcome(
        MediaIntegrityResultDto result,
        MediaIntegrityStatus previousStatus,
        boolean alertRequired) {
}
