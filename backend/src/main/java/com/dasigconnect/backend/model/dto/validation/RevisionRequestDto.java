package com.dasigconnect.backend.model.dto.validation;

import jakarta.validation.constraints.NotBlank;

public class RevisionRequestDto {

    @NotBlank
    private String remarks;

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
