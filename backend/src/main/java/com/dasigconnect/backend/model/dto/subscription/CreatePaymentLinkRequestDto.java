package com.dasigconnect.backend.model.dto.subscription;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreatePaymentLinkRequestDto(
        @NotNull UUID planId,
        @Email @Size(max = 320) String payerEmail) {}
