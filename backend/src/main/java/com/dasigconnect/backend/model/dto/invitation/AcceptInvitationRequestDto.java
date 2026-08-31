package com.dasigconnect.backend.model.dto.invitation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequestDto(
        @NotBlank String token,
        @NotBlank @Size(min = 1, max = 100)
        @Pattern(regexp = "^[\\p{L}][\\p{L} '\\-]*$", message = "must contain only letters, spaces, hyphens, or apostrophes")
        String firstName,
        @NotBlank @Size(min = 1, max = 100)
        @Pattern(regexp = "^[\\p{L}][\\p{L} '\\-]*$", message = "must contain only letters, spaces, hyphens, or apostrophes")
        String lastName,
        @NotBlank @Size(min = 12) String password) {}
