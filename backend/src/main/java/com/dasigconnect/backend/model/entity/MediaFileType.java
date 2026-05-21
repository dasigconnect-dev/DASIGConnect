package com.dasigconnect.backend.model.entity;

public enum MediaFileType {
    jpeg, png, webp, gif, mp4, mov, webm;

    public boolean isVideo() {
        return this == mp4 || this == mov || this == webm;
    }

    public boolean isImage() {
        return !isVideo();
    }
}
