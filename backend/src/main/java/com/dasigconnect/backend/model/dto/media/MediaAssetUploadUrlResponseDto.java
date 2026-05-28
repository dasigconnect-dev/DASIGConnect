package com.dasigconnect.backend.model.dto.media;

public class MediaAssetUploadUrlResponseDto {

    private String signedUrl;
    private String publicUrl;
    private String path;

    public MediaAssetUploadUrlResponseDto(String signedUrl, String publicUrl, String path) {
        this.signedUrl = signedUrl;
        this.publicUrl = publicUrl;
        this.path = path;
    }

    public String getSignedUrl() { return signedUrl; }
    public String getPublicUrl() { return publicUrl; }
    public String getPath() { return path; }
}
