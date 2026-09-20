package com.dasigconnect.backend.service;

import com.dasigconnect.backend.schedule.AbandonmentDetectorJob;
import com.dasigconnect.backend.schedule.EmbeddingFailureDigestJob;
import com.dasigconnect.backend.schedule.EmbeddingReconciliationJob;
import com.dasigconnect.backend.schedule.EmptyScheduleWarningJob;
import com.dasigconnect.backend.schedule.GeneratedWatermarkPurgeJob;
import com.dasigconnect.backend.schedule.InvitationExpiryJob;
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
import java.util.concurrent.CompletableFuture;
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
            TokenPublishingEscalationJob tokenPublishingEscalation,
            ValidationDeadlineNotificationJob validationDeadlineNotification,
            EmbeddingReconciliationJob embeddingReconciliation,
            SocialEngagementSyncJob socialEngagementSync,
            MediaAssetRetentionPurgeJob mediaAssetRetentionPurge,
            StaleDraftSlotReleaseJob staleDraftSlotRelease,
            TokenHealthCheckJob tokenHealthCheck,
            ScheduledJobRunRetentionJob scheduledJobRunRetention,
            EmbeddingFailureDigestJob embeddingFailureDigest,
            EmptyScheduleWarningJob emptyScheduleWarning,
            GeneratedWatermarkPurgeJob generatedWatermarkPurge,
            InvitationExpiryJob invitationExpiry) {
        jobs.put("PublishingSchedulerJob", publishingScheduler::run);
        jobs.put("ReviewLockCleanupJob", reviewLockCleanup::releaseExpiredLocks);
        jobs.put("StaleSubmissionDetectorJob", staleSubmissionDetector::run);
        jobs.put("AbandonmentDetectorJob", abandonmentDetector::run);
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
        jobs.put("GeneratedWatermarkPurgeJob", generatedWatermarkPurge::purgeExpiredGeneratedWatermarks);
        jobs.put("InvitationExpiryJob", invitationExpiry::run);
    }

    /**
     * These two jobs call FacebookPublisherService.publishMediaLinks per
     * submission, which retries up to 3 times with 5s/25s/125s backoff sleeps
     * each -- running them synchronously on the HTTP request thread (as every
     * other job here safely can) risked minutes-long requests, with a client
     * or gateway timeout showing the admin a false failure while the job kept
     * running regardless (found 2026-09-18, alongside the identical-shaped
     * transaction-boundary issue in FastTrackPublishingListener).
     */
    private static final Set<String> ASYNC_JOB_KEYS = Set.of(
            "PublishingSchedulerJob", "TokenPublishingEscalationJob");

    public Set<String> runnableJobKeys() {
        return jobs.keySet();
    }

    public boolean canRun(String jobKey) {
        return jobs.containsKey(jobKey);
    }

    public boolean runsAsynchronously(String jobKey) {
        return ASYNC_JOB_KEYS.contains(jobKey);
    }

    /**
     * Runs the job. Most jobs run synchronously on the caller's thread, so the
     * response already reflects the completed run -- see {@link #ASYNC_JOB_KEYS}
     * for the two that don't. Throws 404 for an unknown key.
     */
    public void run(String jobKey) {
        Runnable job = jobs.get(jobKey);
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown background job: " + jobKey);
        }
        log.info("Manual run requested for {}", jobKey);
        if (ASYNC_JOB_KEYS.contains(jobKey)) {
            CompletableFuture.runAsync(() -> {
                try {
                    job.run();
                } catch (Exception ex) {
                    log.error("Manual async run of {} failed: {}", jobKey, ex.getMessage(), ex);
                }
            });
            return;
        }
        job.run();
    }
}
