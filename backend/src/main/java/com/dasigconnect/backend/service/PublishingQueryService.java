package com.dasigconnect.backend.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
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
     * Atomically claims a due submission before the Facebook API call.
     * This prevents overlapping scheduler runs or multiple app instances from
     * publishing the same scheduled row while the first run is still in flight.
     */
    @Transactional
    public Optional<Submission> claimForPublishing(Submission dueSubmission) {
        SubmissionStatus current = dueSubmission.getStatus();
        SubmissionStatus claimed = switch (current) {
            case scheduled -> SubmissionStatus.publishing;
            case direct_post_scheduled -> SubmissionStatus.direct_post_publishing;
            default -> null;
        };
        if (claimed == null) {
            return Optional.empty();
        }

        int updated = submissionRepository.claimForPublishing(
                dueSubmission.getId(),
                current,
                claimed);
        if (updated != 1) {
            return Optional.empty();
        }
        return submissionRepository.findById(dueSubmission.getId());
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
