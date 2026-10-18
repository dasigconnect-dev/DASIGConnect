package com.dasigconnect.backend.model.dto.subscription;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ExtendGracePeriodRequestDto(@Min(1) @Max(30) int days) {
}
