package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ManualJobRunnerTest {

    @Mock private PublishingSchedulerJob publishingSchedulerJob;
    @Mock private ReviewLockCleanupJob reviewLockCleanupJob;
    @Mock private StaleSubmissionDetectorJob staleSubmissionDetectorJob;
    @Mock private AbandonmentDetectorJob abandonmentDetectorJob;
    @Mock private TokenPublishingEscalationJob tokenPublishingEscalationJob;
    @Mock private ValidationDeadlineNotificationJob validationDeadlineNotificationJob;
    @Mock private EmbeddingReconciliationJob embeddingReconciliationJob;
    @Mock private SocialEngagementSyncJob socialEngagementSyncJob;
    @Mock private MediaAssetRetentionPurgeJob mediaAssetRetentionPurgeJob;
    @Mock private StaleDraftSlotReleaseJob staleDraftSlotReleaseJob;
    @Mock private TokenHealthCheckJob tokenHealthCheckJob;
    @Mock private ScheduledJobRunRetentionJob scheduledJobRunRetentionJob;
    @Mock private EmbeddingFailureDigestJob embeddingFailureDigestJob;
    @Mock private EmptyScheduleWarningJob emptyScheduleWarningJob;
    @Mock private GeneratedWatermarkPurgeJob generatedWatermarkPurgeJob;
    @Mock private InvitationExpiryJob invitationExpiryJob;

    private ManualJobRunner runner() {
        return new ManualJobRunner(
                publishingSchedulerJob,
                reviewLockCleanupJob,
                staleSubmissionDetectorJob,
                abandonmentDetectorJob,
                tokenPublishingEscalationJob,
                validationDeadlineNotificationJob,
                embeddingReconciliationJob,
                socialEngagementSyncJob,
                mediaAssetRetentionPurgeJob,
                staleDraftSlotReleaseJob,
                tokenHealthCheckJob,
                scheduledJobRunRetentionJob,
                embeddingFailureDigestJob,
                emptyScheduleWarningJob,
                generatedWatermarkPurgeJob,
                invitationExpiryJob);
    }

    @Test
    void run_unknownJobKey_throwsNotFound() {
        assertThatThrownBy(() -> runner().run("NotARealJob"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void run_mostJobs_runSynchronouslyOnTheCallingThread() {
        runner().run("ReviewLockCleanupJob");

        // No wait needed -- if this were async, the verify below could still
        // pass on a fast machine, which is exactly why the synchronous case
        // needs no latch/timing trick: run() returning at all already proves
        // releaseExpiredLocks() executed, since nothing else could have set it.
        verify(reviewLockCleanupJob).releaseExpiredLocks();
    }

    @Test
    void runsAsynchronously_reflectsOnlyTheTwoFacebookApiJobs() {
        ManualJobRunner runner = runner();

        assertThat(runner.runsAsynchronously("PublishingSchedulerJob")).isTrue();
        assertThat(runner.runsAsynchronously("TokenPublishingEscalationJob")).isTrue();
        assertThat(runner.runsAsynchronously("ReviewLockCleanupJob")).isFalse();
        assertThat(runner.runsAsynchronously("EmptyScheduleWarningJob")).isFalse();
    }

    @Test
    void run_publishingSchedulerJob_doesNotBlockTheCallingThreadOnASlowRun() throws InterruptedException {
        // Regression 2026-09-18: PublishingSchedulerJob makes live Facebook API
        // calls with up to 3 retries and up to 125s of backoff sleep each --
        // running it synchronously on the HTTP request thread risked a
        // multi-minute request and a false-failure client/gateway timeout.
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(inv -> {
            started.countDown();
            release.await(2, TimeUnit.SECONDS);
            return null;
        }).when(publishingSchedulerJob).run();

        long before = System.currentTimeMillis();
        runner().run("PublishingSchedulerJob");
        long elapsedMs = System.currentTimeMillis() - before;

        assertThat(elapsedMs).isLessThan(500);
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        release.countDown();
    }
}
