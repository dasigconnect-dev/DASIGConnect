package com.dasigconnect.backend.model.entity;

public enum MediaAssetStatus {
    /** Uploaded to a submission that is still a DRAFT; not yet bound to an institution. */
    STAGED,
    PROCESSING,
    READY,
    FAILED,
    DELETED
}
