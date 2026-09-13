package com.dasigconnect.backend.model.dto.submission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class AttachMediaDto {

    @NotBlank(message = "storageUrl is required")
    private String storageUrl;

    @NotBlank(message = "fileName is required")
    private String fileName;

    @NotBlank(message = "fileType is required")
    private String fileType;

    @NotNull(message = "fileSizeBytes is required")
    @Positive(message = "fileSizeBytes must be positive")
    private Long fileSizeBytes;

    /**
     * Optional target album name for a media asset uploaded during review —
     * resolved / created the same way as the contributor's album assignment.
     * Ignored for draft uploads (those are filed at submit time); when blank on
     * a non-draft upload the asset is filed into the submission's own album.
     */
    private String albumName;

    public String getStorageUrl() { return storageUrl; }
    public void setStorageUrl(String storageUrl) { this.storageUrl = storageUrl; }

    public String getAlbumName() { return albumName; }
    public void setAlbumName(String albumName) { this.albumName = albumName; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
}
