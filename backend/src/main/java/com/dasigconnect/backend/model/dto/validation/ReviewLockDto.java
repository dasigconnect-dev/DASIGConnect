package com.dasigconnect.backend.model.dto.validation;

import java.time.Instant;
import java.util.UUID;

import com.dasigconnect.backend.model.entity.ReviewLock;

public class ReviewLockDto {

    private UUID id;
    private UUID submissionId;
    private UUID lockedById;
    private String lockedByEmail;
    private Instant acquiredAt;
    private Instant expiresAt;

    public static ReviewLockDto from(ReviewLock lock) {
        ReviewLockDto dto = new ReviewLockDto();
        dto.id = lock.getId();
        dto.submissionId = lock.getSubmission().getId();
        dto.lockedById = lock.getLockedBy().getId();
        dto.lockedByEmail = lock.getLockedBy().getEmail();
        dto.acquiredAt = lock.getAcquiredAt();
        dto.expiresAt = lock.getExpiresAt();
        return dto;
    }

    public UUID getId() { return id; }
    public UUID getSubmissionId() { return submissionId; }
    public UUID getLockedById() { return lockedById; }
    public String getLockedByEmail() { return lockedByEmail; }
    public Instant getAcquiredAt() { return acquiredAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
