package com.dasigconnect.backend.model.dto.subscription;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record CreatePaymentLinkRequestDto(
        @Email @Size(max = 320) String payerEmail) {}
