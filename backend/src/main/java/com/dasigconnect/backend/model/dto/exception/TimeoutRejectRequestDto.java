package com.dasigconnect.backend.model.dto.exception;

public class TimeoutRejectRequestDto {

    private String reasonCode;
    private String notes;

    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
