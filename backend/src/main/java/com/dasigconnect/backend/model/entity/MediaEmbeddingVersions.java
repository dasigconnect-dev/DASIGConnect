package com.dasigconnect.backend.model.entity;

/**
 * Processing-version identifiers shared by embedding producers and readers.
 * Keeping them outside the service and repository layers prevents either side
 * from silently drifting to a different index generation.
 */
public final class MediaEmbeddingVersions {

    public static final String IMAGE = "image-embedding-v1";
    public static final String SEMANTIC = "semantic-embedding-v2";

    private MediaEmbeddingVersions() {
    }
}
