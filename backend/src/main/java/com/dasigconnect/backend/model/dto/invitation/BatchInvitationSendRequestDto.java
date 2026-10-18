package com.dasigconnect.backend.model.dto.invitation;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BatchInvitationSendRequestDto(
        @NotNull List<BatchInvitationRowDto> rows
) {}

