package com.dasigconnect.backend.model.dto.validation;

import jakarta.validation.constraints.NotBlank;

public class RejectionRequestDto {

    @NotBlank
    private String reasonCode;

    private String notes;

    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
