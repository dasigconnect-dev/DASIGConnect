package com.dasigconnect.backend.service;

import com.dasigconnect.backend.external.ClaudeVisionClient;
import com.dasigconnect.backend.model.dto.ai.CaptionResponseDto;
import com.dasigconnect.backend.model.dto.ai.CaptionVariantDto;
import com.dasigconnect.backend.model.entity.AiInteractionLog;
import com.dasigconnect.backend.model.entity.MediaAsset;
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
import java.util.StringJoiner;
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
     * Generates one caption variant for the caller-selected style.
     * Validates that the submission belongs to the caller's institution.
     */
    public CaptionResponseDto generateCaptions(UUID submissionId, UUID userId, UUID institutionId,
                                               String existingCaption, String prompt, String tone) {
        int requestedWords = ClaudeVisionClient.extractRequestedWordCount(prompt);
        if (requestedWords > ClaudeVisionClient.MAX_REQUESTED_CAPTION_WORDS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "AI captions are limited to "
                            + ClaudeVisionClient.MAX_REQUESTED_CAPTION_WORDS
                            + " requested words.");
        }

        // Step 1: Fetch submission data in a short read-only transaction
        SubmissionContext ctx = loadSubmissionContext(submissionId, institutionId);

        // Step 2: Call Claude outside any transaction (timeout enforced by client)
        List<CaptionVariantDto> variants = claudeVisionClient.generateCaptions(
                ctx.imageUrls(),
                ctx.mediaMetadata(),
                ctx.eventTitle(),
                ctx.eventDate(),
                ctx.institutionName(),
                ctx.category(),
                existingCaption,
                prompt,
                tone);

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
        Submission submission = submissionRepository.findByIdWithInstitution(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Submission not found."));

        // institutionId is null for administrators — they can generate for any institution
        if (institutionId != null && !submission.getInstitution().getId().equals(institutionId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Submission does not belong to your institution.");
        }

        List<SubmissionMediaAsset> junctionRows =
                submissionMediaAssetRepository.findBySubmissionIdWithMediaAsset(submissionId);

        List<MediaAsset> imageAssets = junctionRows.stream()
                .map(SubmissionMediaAsset::getMediaAsset)
                .filter(asset -> asset.getFileType() != null && asset.getFileType().isImage())
                .limit(4)
                .toList();

        String mediaMetadata = buildSelectedMediaMetadata(imageAssets);
        List<String> imageUrls = imageAssets.stream()
                .filter(asset -> !hasUsableCaptionMetadata(asset))
                .filter(asset -> hasText(asset.getStorageUrl()))
                .map(MediaAsset::getStorageUrl)
                .toList();

        UUID resolvedInstitutionId = institutionId != null
                ? institutionId
                : submission.getInstitution().getId();

        String eventDate = submission.getEventDate() == null ? null : submission.getEventDate().toString();
        String institutionName = submission.getInstitution() == null ? null : submission.getInstitution().getName();

        return new SubmissionContext(
                imageUrls,
                mediaMetadata,
                submission.getEventTitle(),
                eventDate,
                institutionName,
                submission.getCategory(),
                resolvedInstitutionId);
    }

    private static String buildSelectedMediaMetadata(List<MediaAsset> assets) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (MediaAsset asset : assets) {
            String metadata = buildAssetMetadata(asset);
            if (metadata.isBlank()) continue;
            sb.append("Media ").append(index++).append(": ").append(metadata).append('\n');
        }
        return sb.toString().trim();
    }

    private static String buildAssetMetadata(MediaAsset asset) {
        StringJoiner joiner = new StringJoiner("; ");
        append(joiner, "file", asset.getFileName());
        append(joiner, "category", asset.getAiCategory());
        append(joiner, "asset_type", asset.getAssetType());
        append(joiner, "description", asset.getAiDescription());
        append(joiner, "visible_objects", asset.getVisibleObjects());
        append(joiner, "specific_subjects", asset.getSpecificSubjects());
        append(joiner, "visual_style", asset.getVisualStyle());
        append(joiner, "dominant_colors", asset.getDominantColors());
        append(joiner, "possible_use_cases", asset.getPossibleUseCases());
        append(joiner, "ai_tags", asset.getAiTags());
        return joiner.toString();
    }

    private static boolean hasUsableCaptionMetadata(MediaAsset asset) {
        return hasText(asset.getAiCategory())
                || hasText(asset.getAssetType())
                || hasText(asset.getAiDescription())
                || hasValues(asset.getVisibleObjects())
                || hasValues(asset.getSpecificSubjects())
                || hasValues(asset.getVisualStyle())
                || hasValues(asset.getDominantColors())
                || hasValues(asset.getPossibleUseCases())
                || hasValues(asset.getAiTags());
    }

    private static void append(StringJoiner joiner, String label, String value) {
        if (!hasText(value)) return;
        joiner.add(label + ": " + value.trim());
    }

    private static void append(StringJoiner joiner, String label, String[] values) {
        if (!hasValues(values)) return;
        joiner.add(label + ": " + String.join(", ", cleaned(values)));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean hasValues(String[] values) {
        return values != null && cleaned(values).length > 0;
    }

    private static String[] cleaned(String[] values) {
        if (values == null) return new String[0];
        return java.util.Arrays.stream(values)
                .filter(CaptionGenerationService::hasText)
                .map(String::trim)
                .distinct()
                .toArray(String[]::new);
    }

    private record SubmissionContext(List<String> imageUrls, String mediaMetadata,
                                     String eventTitle, String eventDate,
                                     String institutionName, String category,
                                     UUID institutionId) {}
}
