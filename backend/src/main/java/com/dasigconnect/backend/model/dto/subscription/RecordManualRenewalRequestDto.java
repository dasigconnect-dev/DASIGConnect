package com.dasigconnect.backend.model.dto.subscription;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record RecordManualRenewalRequestDto(
        @Min(1) @Max(12) int billingMonths,
        @Size(max = 120) String paymentReference) {
}
