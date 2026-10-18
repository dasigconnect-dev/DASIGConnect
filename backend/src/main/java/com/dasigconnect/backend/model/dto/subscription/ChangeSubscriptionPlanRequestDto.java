package com.dasigconnect.backend.model.dto.subscription;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ChangeSubscriptionPlanRequestDto(@NotNull UUID planId) {
}
