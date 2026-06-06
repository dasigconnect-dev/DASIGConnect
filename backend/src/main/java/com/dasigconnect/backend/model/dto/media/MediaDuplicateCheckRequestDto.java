package com.dasigconnect.backend.model.dto.media;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/**
 * Upload-time exact-duplicate check: the browser computes each file's SHA-256 before upload and
 * asks whether the same bytes already exist in the target institution.
 */
public class MediaDuplicateCheckRequestDto {

    /** Required for administrators (the target institution); ignored for institution-scoped users. */
    private UUID institutionId;

    @NotEmpty
    private List<String> sha256s;

    public UUID getInstitutionId() { return institutionId; }
    public void setInstitutionId(UUID institutionId) { this.institutionId = institutionId; }
    public List<String> getSha256s() { return sha256s; }
    public void setSha256s(List<String> sha256s) { this.sha256s = sha256s; }
}
