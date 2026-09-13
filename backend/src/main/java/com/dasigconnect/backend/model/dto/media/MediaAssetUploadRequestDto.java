package com.dasigconnect.backend.model.dto.media;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public class MediaAssetUploadRequestDto {

    @NotBlank
    private String storageUrl;

    @NotBlank
    private String fileName;

    @jakarta.validation.constraints.Pattern(regexp = "^[0-9a-fA-F]{64}$", message = "Content hash must be SHA-256.")
    private String contentHash;

    private boolean allowDuplicate;

    @NotBlank
    private String fileType;

    @NotNull
    private Long fileSizeBytes;

    private UUID institutionId;
    private UUID albumId;

    @Size(max = 255)
    private String albumName;

    private boolean autoMatchAlbum;

    @Size(max = 20)
    private List<@NotBlank @Size(max = 50) String> tags;

    public String getStorageUrl() {
        return storageUrl;
    }

    public void setStorageUrl(String storageUrl) {
        this.storageUrl = storageUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
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

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public UUID getInstitutionId() {
        return institutionId;
    }

    public void setInstitutionId(UUID institutionId) {
        this.institutionId = institutionId;
    }

    public UUID getAlbumId() {
        return albumId;
    }

    public void setAlbumId(UUID albumId) {
        this.albumId = albumId;
    }

    public String getAlbumName() {
        return albumName;
    }

    public void setAlbumName(String albumName) {
        this.albumName = albumName;
    }

    public boolean isAutoMatchAlbum() {
        return autoMatchAlbum;
    }

    public void setAutoMatchAlbum(boolean autoMatchAlbum) {
        this.autoMatchAlbum = autoMatchAlbum;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}
