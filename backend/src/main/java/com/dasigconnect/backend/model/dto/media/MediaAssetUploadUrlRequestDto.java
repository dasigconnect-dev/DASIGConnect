package com.dasigconnect.backend.model.dto.media;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public class MediaAssetUploadUrlRequestDto {

    @NotBlank
    private String fileName;

    @NotBlank
    private String fileType;

    /** Required for administrators; ignored for institution-scoped users. */
    private UUID institutionId;

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public UUID getInstitutionId() { return institutionId; }
    public void setInstitutionId(UUID institutionId) { this.institutionId = institutionId; }
}
