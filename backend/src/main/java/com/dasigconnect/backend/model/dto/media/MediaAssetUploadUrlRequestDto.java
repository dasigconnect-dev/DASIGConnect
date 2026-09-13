package com.dasigconnect.backend.model.dto.media;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public class MediaAssetUploadUrlRequestDto {

    @NotBlank
    private String fileName;

    @NotBlank
    private String fileType;

    @jakarta.validation.constraints.Pattern(regexp = "^[0-9a-fA-F]{64}$", message = "Content hash must be SHA-256.")
    private String contentHash;

    private boolean allowDuplicate;

    /**
     * Target institution (admins uploading network-wide); falls back to the
     * caller's institution.
     */
    private UUID institutionId;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public boolean isAllowDuplicate() {
        return allowDuplicate;
    }

    public void setAllowDuplicate(boolean allowDuplicate) {
        this.allowDuplicate = allowDuplicate;
    }

    public UUID getInstitutionId() {
        return institutionId;
    }

    public void setInstitutionId(UUID institutionId) {
        this.institutionId = institutionId;
    }
}
