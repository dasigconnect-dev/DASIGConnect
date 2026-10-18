package com.dasigconnect.backend.model.dto.invitation;

import java.util.List;
import java.util.UUID;

public record BatchInvitationSendResponseDto(
        UUID batchId,
        int rowCount,
        int sentCount,
        int rejectedCount,
        List<BatchInvitationRowPreviewResultDto> results
) {}

