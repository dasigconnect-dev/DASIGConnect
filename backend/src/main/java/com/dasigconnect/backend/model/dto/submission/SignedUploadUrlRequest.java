package com.dasigconnect.backend.model.dto.submission;

import jakarta.validation.constraints.NotBlank;

public class SignedUploadUrlRequest {

    @NotBlank
    private String fileName;

    @NotBlank
    private String fileType;

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
}
