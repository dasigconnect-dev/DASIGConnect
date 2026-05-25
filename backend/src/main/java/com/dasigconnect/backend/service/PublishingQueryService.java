package com.dasigconnect.backend.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;

/**
 * Transactional read queries for the publishing pipeline.
 *
 * Exists as a separate @Service so PublishingSchedulerJob can call these
 * methods through the Spring proxy and get a real transaction — calling
 * @Transactional methods directly within the same bean bypasses the proxy
 * (self-invocation) and silently drops transaction wrapping, which causes
 * LazyInitializationException when lazy associations are accessed after
 * the repository's own short transaction has already closed.
 */
@Service
@Transactional(readOnly = true)
public class PublishingQueryService {

    private final SubmissionRepository submissionRepository;
    private final SubmissionMediaAssetRepository submissionMediaAssetRepository;

    public PublishingQueryService(
            SubmissionRepository submissionRepository,
            SubmissionMediaAssetRepository submissionMediaAssetRepository) {
        this.submissionRepository = submissionRepository;
        this.submissionMediaAssetRepository = submissionMediaAssetRepository;
    }

    /**
     * Returns SCHEDULED submissions whose scheduledAt falls inside [from, to].
     * Called by PublishingSchedulerJob each minute with a 5-minute lookback window.
     */
    public List<Submission> loadDueSubmissions(Instant from, Instant to) {
        return submissionRepository.findScheduledInPublishWindow(from, to);
    }

    /**
     * Returns the ordered MediaAsset list for a submission.
     * Uses JOIN FETCH so mediaAsset is fully loaded within this transaction
     * and safe to read after the session closes.
     */
    public List<MediaAsset> loadAssetsForSubmission(UUID submissionId) {
        return submissionMediaAssetRepository
                .findBySubmissionIdWithMediaAsset(submissionId)
                .stream()
                .map(sma -> sma.getMediaAsset())
                .toList();
    }
}
