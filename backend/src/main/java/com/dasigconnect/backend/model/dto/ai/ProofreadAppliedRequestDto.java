package com.dasigconnect.backend.model.dto.ai;

import java.util.UUID;

/** One suggested writing fix was applied. {@code submissionId} is optional (unsaved draft). */
public record ProofreadAppliedRequestDto(UUID submissionId) {}
