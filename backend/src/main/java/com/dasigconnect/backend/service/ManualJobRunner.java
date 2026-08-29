package com.dasigconnect.backend.service;

import com.dasigconnect.backend.schedule.AbandonmentDetectorJob;
import com.dasigconnect.backend.schedule.EmbeddingFailureDigestJob;
import com.dasigconnect.backend.schedule.EmbeddingReconciliationJob;
import com.dasigconnect.backend.schedule.EmptyScheduleWarningJob;
import com.dasigconnect.backend.schedule.ExpiredOverrideCleanupJob;
import com.dasigconnect.backend.schedule.MediaAssetRetentionPurgeJob;
import com.dasigconnect.backend.schedule.PublishingSchedulerJob;
import com.dasigconnect.backend.schedule.ReviewLockCleanupJob;
import com.dasigconnect.backend.schedule.ScheduledJobRunRetentionJob;
import com.dasigconnect.backend.schedule.SocialEngagementSyncJob;
import com.dasigconnect.backend.schedule.StaleDraftSlotReleaseJob;
import com.dasigconnect.backend.schedule.StaleSubmissionDetectorJob;
import com.dasigconnect.backend.schedule.TokenHealthCheckJob;
import com.dasigconnect.backend.schedule.TokenPublishingEscalationJob;
import com.dasigconnect.backend.schedule.ValidationDeadlineNotificationJob;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lets an admin run any scheduled job on demand from the System Health screen.
 * Keys match {@code ScheduledJobRun.jobName} / {@code SystemHealthService.EXPECTED_JOBS}
 * (the job's simple class name). Running a job manually just does what its next
 * scheduled tick would do now — the jobs' own claim/idempotency guards still apply.
 */
@Service
public class ManualJobRunner {

    private static final Logger log = LoggerFactory.getLogger(ManualJobRunner.class);

    private final Map<String, Runnable> jobs = new LinkedHashMap<>();

    public ManualJobRunner(
            PublishingSchedulerJob publishingScheduler,
            ReviewLockCleanupJob reviewLockCleanup,
            StaleSubmissionDetectorJob staleSubmissionDetector,
            AbandonmentDetectorJob abandonmentDetector,
            ExpiredOverrideCleanupJob expiredOverrideCleanup,
            TokenPublishingEscalationJob tokenPublishingEscalation,
            ValidationDeadlineNotificationJob validationDeadlineNotification,
            EmbeddingReconciliationJob embeddingReconciliation,
            SocialEngagementSyncJob socialEngagementSync,
            MediaAssetRetentionPurgeJob mediaAssetRetentionPurge,
            StaleDraftSlotReleaseJob staleDraftSlotRelease,
            TokenHealthCheckJob tokenHealthCheck,
            ScheduledJobRunRetentionJob scheduledJobRunRetention,
            EmbeddingFailureDigestJob embeddingFailureDigest,
            EmptyScheduleWarningJob emptyScheduleWarning) {
        jobs.put("PublishingSchedulerJob", publishingScheduler::run);
        jobs.put("ReviewLockCleanupJob", reviewLockCleanup::releaseExpiredLocks);
        jobs.put("StaleSubmissionDetectorJob", staleSubmissionDetector::run);
        jobs.put("AbandonmentDetectorJob", abandonmentDetector::run);
        jobs.put("ExpiredOverrideCleanupJob", expiredOverrideCleanup::run);
        jobs.put("TokenPublishingEscalationJob", tokenPublishingEscalation::run);
        jobs.put("ValidationDeadlineNotificationJob", validationDeadlineNotification::checkValidationDeadlines);
        jobs.put("EmbeddingReconciliationJob", embeddingReconciliation::reconcile);
        jobs.put("SocialEngagementSyncJob", socialEngagementSync::syncPendingEngagement);
        jobs.put("MediaAssetRetentionPurgeJob", mediaAssetRetentionPurge::purgeExpiredDeletedAssets);
        jobs.put("StaleDraftSlotReleaseJob", staleDraftSlotRelease::releaseStaleSlots);
        jobs.put("TokenHealthCheckJob", tokenHealthCheck::run);
        jobs.put("ScheduledJobRunRetentionJob", scheduledJobRunRetention::pruneOldRuns);
        jobs.put("EmbeddingFailureDigestJob", embeddingFailureDigest::scanFailedEmbeddings);
        jobs.put("EmptyScheduleWarningJob", emptyScheduleWarning::scanEmptySchedules);
    }

    public Set<String> runnableJobKeys() {
        return jobs.keySet();
    }

    public boolean canRun(String jobKey) {
        return jobs.containsKey(jobKey);
    }

    /** Runs the job synchronously on the caller's thread. Throws 404 for an unknown key. */
    public void run(String jobKey) {
        Runnable job = jobs.get(jobKey);
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown background job: " + jobKey);
        }
        log.info("Manual run requested for {}", jobKey);
        job.run();
    }
}
