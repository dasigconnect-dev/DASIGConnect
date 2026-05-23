package com.dasigconnect.backend.model.dto.submission;

public class SignedUploadUrlResponse {

    private final String signedUrl;
    private final String publicUrl;
    private final String path;

    public SignedUploadUrlResponse(String signedUrl, String publicUrl, String path) {
        this.signedUrl = signedUrl;
        this.publicUrl = publicUrl;
        this.path = path;
    }

    public String getSignedUrl() { return signedUrl; }
    public String getPublicUrl() { return publicUrl; }
    public String getPath() { return path; }
}
