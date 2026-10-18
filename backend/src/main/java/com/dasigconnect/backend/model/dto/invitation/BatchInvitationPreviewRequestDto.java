package com.dasigconnect.backend.model.dto.invitation;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BatchInvitationPreviewRequestDto(
        @NotNull List<BatchInvitationRowDto> rows
) {}

