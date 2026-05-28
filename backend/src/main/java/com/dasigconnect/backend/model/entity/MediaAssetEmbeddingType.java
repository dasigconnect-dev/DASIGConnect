package com.dasigconnect.backend.model.entity;

public enum MediaAssetEmbeddingType {
    IMAGE("image"),
    SEMANTIC("semantic"),
    KEYFRAME("keyframe");

    private final String dbValue;

    MediaAssetEmbeddingType(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }
}
