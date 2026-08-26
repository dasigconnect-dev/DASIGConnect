package com.dasigconnect.backend.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionEngagementMetric;
import com.dasigconnect.backend.repository.SubmissionEngagementMetricRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.service.FacebookEngagementAnalyticsClient.PostEngagement;

/** UC-3.4: syncs per-post Facebook engagement (reach, reactions, comments, shares) into submission_engagement_metrics. */
@Service
public class SocialEngagementSyncService {

    private static final Logger log = LoggerFactory.getLogger(SocialEngagementSyncService.class);

    private final SubmissionEngagementMetricRepository engagementMetricRepository;
    private final SubmissionRepository submissionRepository;
    private final FacebookEngagementAnalyticsClient facebookClient;
    private final int batchSize;

    public SocialEngagementSyncService(
            SubmissionEngagementMetricRepository engagementMetricRepository,
            SubmissionRepository submissionRepository,
            FacebookEngagementAnalyticsClient facebookClient,
            @Value("${app.social-engagement.sync-batch-size:25}") int batchSize) {
        this.engagementMetricRepository = engagementMetricRepository;
        this.submissionRepository = submissionRepository;
        this.facebookClient = facebookClient;
        this.batchSize = Math.min(Math.max(batchSize, 1), 100);
    }

    public int syncPending() {
        List<UUID> submissionIds = engagementMetricRepository
                .findPublishedSubmissionIdsMissingEngagement(PageRequest.of(0, batchSize));
        int synced = 0;
        for (UUID submissionId : submissionIds) {
            if (syncOne(submissionId)) {
                synced++;
            }
        }
        return synced;
    }

    @Transactional
    boolean syncOne(UUID submissionId) {
        Submission submission = submissionRepository.findById(submissionId).orElse(null);
        if (submission == null || submission.getPlatformPostId() == null) {
            return false;
        }
        SubmissionEngagementMetric metric = engagementMetricRepository.findBySubmissionId(submissionId)
                .orElseGet(() -> {
                    SubmissionEngagementMetric created = new SubmissionEngagementMetric();
                    created.setSubmission(submission);
                    return created;
                });
        metric.setLastAttemptAt(Instant.now());
        try {
            PostEngagement engagement = facebookClient.fetchPostEngagement(submission.getPlatformPostId());
            metric.setReach(engagement.reach());
            metric.setReactions(engagement.reactions());
            metric.setCommentsCount(engagement.comments());
            metric.setShares(engagement.shares());
            metric.setFetchedAt(Instant.now());
            metric.setLastError(null);
            engagementMetricRepository.save(metric);
            return true;
        } catch (Exception ex) {
            metric.setLastError(ex.getMessage());
            engagementMetricRepository.save(metric);
            log.warn("Failed to sync Facebook engagement for submission {}: {}", submissionId, ex.getMessage());
            return false;
        }
    }
}
