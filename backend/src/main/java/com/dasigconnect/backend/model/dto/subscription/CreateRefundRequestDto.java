package com.dasigconnect.backend.model.dto.subscription;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRefundRequestDto(
        @Min(1) long amountCentavos,
        @NotBlank @Size(max = 100) String reason,
        @Size(max = 255) String notes) {}
