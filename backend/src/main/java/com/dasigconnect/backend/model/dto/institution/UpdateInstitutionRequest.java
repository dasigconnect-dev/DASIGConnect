package com.dasigconnect.backend.model.dto.institution;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for PUT /api/v1/institutions/{id}.
 *
 * Allows updating the institution name and/or email domain.
 * The institution code is not editable after creation.
 */
public class UpdateInstitutionRequest {

    @NotBlank(message = "Institution name is required.")
    @Size(min = 2, max = 255, message = "Institution name must be between 2 and 255 characters.")
    private String name;

    @NotBlank(message = "Email domain is required.")
    @Pattern(
            regexp = "^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$",
            message = "Email domain must be valid (e.g. su.edu.ph)."
    )
    private String emailDomain;

    public UpdateInstitutionRequest() {}

    public UpdateInstitutionRequest(String name, String emailDomain) {
        this.name = name;
        this.emailDomain = emailDomain;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmailDomain() {
        return emailDomain;
    }

    public void setEmailDomain(String emailDomain) {
        this.emailDomain = emailDomain;
    }

    @Override
    public String toString() {
        return "UpdateInstitutionRequest{"
                + "name='" + name + '\''
                + ", emailDomain='" + emailDomain + '\''
                + '}';
    }
}
