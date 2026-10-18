package com.dasigconnect.backend.model.dto.invitation;

import java.util.List;

public record BatchInvitationPreviewResponseDto(
        List<BatchInvitationRowPreviewResultDto> rows,
        int totalRows,
        int validCount,
        int invalidCount
) {}

