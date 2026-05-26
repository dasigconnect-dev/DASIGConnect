package com.dasigconnect.backend.service;

import com.dasigconnect.backend.external.ClaudeVisionClient;
import com.dasigconnect.backend.model.dto.ai.CaptionResponseDto;
import com.dasigconnect.backend.model.dto.ai.CaptionVariantDto;
import com.dasigconnect.backend.model.entity.AiInteractionLog;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionMediaAsset;
import com.dasigconnect.backend.repository.AiInteractionLogRepository;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrates on-demand AI caption generation (UC-3.2).
 *
 * Transaction discipline: DB reads happen in a short read-only transaction.
 * The ClaudeVisionClient call executes OUTSIDE any open transaction so the
 * HikariCP connection pool is not held during the external HTTP round-trip.
 */
@Service
public class CaptionGenerationService {

    private final SubmissionRepository submissionRepository;
    private final SubmissionMediaAssetRepository submissionMediaAssetRepository;
    private final ClaudeVisionClient claudeVisionClient;
    private final AiInteractionLogRepository aiInteractionLogRepository;

    public CaptionGenerationService(SubmissionRepository submissionRepository,
                                    SubmissionMediaAssetRepository submissionMediaAssetRepository,
                                    ClaudeVisionClient claudeVisionClient,
                                    AiInteractionLogRepository aiInteractionLogRepository) {
        this.submissionRepository = submissionRepository;
        this.submissionMediaAssetRepository = submissionMediaAssetRepository;
        this.claudeVisionClient = claudeVisionClient;
        this.aiInteractionLogRepository = aiInteractionLogRepository;
    }

    /**
     * Generates three caption variants (professional / community / energetic).
     * Validates that the submission belongs to the caller's institution and has at least one image.
     */
    public CaptionResponseDto generateCaptions(UUID submissionId, UUID userId, UUID institutionId) {
        // Step 1: Fetch submission data in a short read-only transaction
        SubmissionContext ctx = loadSubmissionContext(submissionId, institutionId);

        // Step 2: Call Claude outside any transaction (10-second timeout enforced by client)
        List<CaptionVariantDto> variants = claudeVisionClient.generateCaptions(
                ctx.imageUrls(), ctx.eventTitle());

        // Step 3: Log generation event in a new transaction
        logInteraction(submissionId, ctx.institutionId(), "re_generate", null);

        return new CaptionResponseDto(submissionId, variants);
    }

    /**
     * Logs a post-generation user action (use / use_then_edited / edit / dismiss).
     * Called via the separate /ai/caption/log endpoint; failures are swallowed so
     * they cannot block the UI.
     */
    @Transactional
    public void logInteraction(UUID submissionId, UUID institutionId,
                               String actionTaken, String toneSelected) {
        try {
            AiInteractionLog entry = new AiInteractionLog();
            entry.setSubmissionId(submissionId);
            entry.setInstitutionId(institutionId);
            entry.setInteractionType("caption_suggestion");
            entry.setActionTaken(actionTaken);
            entry.setToneSelected(toneSelected);
            aiInteractionLogRepository.save(entry);
        } catch (Exception e) {
            // Never let logging fail the caller
        }
    }

    // No @Transactional here — each repository call runs in its own short implicit transaction.
    // This ensures no DB connection is held when claudeVisionClient.generateCaptions() is called.
    private SubmissionContext loadSubmissionContext(UUID submissionId, UUID institutionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Submission not found."));

        // institutionId is null for administrators — they can generate for any institution
        if (institutionId != null && !submission.getInstitution().getId().equals(institutionId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Submission does not belong to your institution.");
        }

        List<SubmissionMediaAsset> junctionRows =
                submissionMediaAssetRepository.findBySubmissionIdWithMediaAsset(submissionId);

        List<String> imageUrls = junctionRows.stream()
                .filter(sma -> sma.getMediaAsset().getFileType().isImage())
                .map(sma -> sma.getMediaAsset().getStorageUrl())
                .limit(4)
                .toList();

        if (imageUrls.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Caption generation requires at least one image asset on the submission.");
        }

        UUID resolvedInstitutionId = institutionId != null
                ? institutionId
                : submission.getInstitution().getId();

        return new SubmissionContext(imageUrls, submission.getEventTitle(), resolvedInstitutionId);
    }

    private record SubmissionContext(List<String> imageUrls, String eventTitle, UUID institutionId) {}
}
