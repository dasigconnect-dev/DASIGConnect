package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.entity.FacebookPostMetric;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.repository.FacebookPostMetricRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.service.FacebookInsightsClient.FacebookPostMetricsSnapshot;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FacebookInsightsSyncService {

    private static final Logger log = LoggerFactory.getLogger(FacebookInsightsSyncService.class);
    private static final int MAX_BATCH_SIZE = 50;

    private final SubmissionRepository submissionRepository;
    private final FacebookPostMetricRepository metricRepository;
    private final FacebookInsightsClient insightsClient;
    private final JdbcTemplate jdbcTemplate;
    private final int batchSize;
    private final int lookbackDays;
    private final int refreshIntervalMinutes;

    public FacebookInsightsSyncService(
            SubmissionRepository submissionRepository,
            FacebookPostMetricRepository metricRepository,
            FacebookInsightsClient insightsClient,
            JdbcTemplate jdbcTemplate,
            @Value("${app.facebook.insights.batch-size:20}") int batchSize,
            @Value("${app.facebook.insights.lookback-days:90}") int lookbackDays,
            @Value("${app.facebook.insights.refresh-interval-minutes:360}") int refreshIntervalMinutes) {
        this.submissionRepository = submissionRepository;
        this.metricRepository = metricRepository;
        this.insightsClient = insightsClient;
        this.jdbcTemplate = jdbcTemplate;
        this.batchSize = Math.min(Math.max(batchSize, 1), MAX_BATCH_SIZE);
        this.lookbackDays = Math.max(lookbackDays, 1);
        this.refreshIntervalMinutes = Math.max(refreshIntervalMinutes, 15);
    }

    /**
     * Result of a sync pass. {@code due} is how many published posts were eligible for a refresh,
     * {@code synced} how many were stored, {@code failed} how many Graph fetches errored, and
     * {@code reason} a human-readable note when nothing could run (skipped) or all fetches failed.
     */
    public record SyncResult(int due, int synced, int failed, String reason) {
        public static SyncResult skipped(String reason) {
            return new SyncResult(0, 0, 0, reason);
        }
    }

    public SyncResult syncDueMetrics() {
        if (!insightsClient.isConfigured()) {
            log.info("Facebook insights sync skipped because Facebook token settings are incomplete.");
            return SyncResult.skipped("Facebook Page token or Page ID is not configured.");
        }
        if (!metricsTableExists()) {
            log.info("Facebook insights sync skipped because facebook_post_metrics is not migrated yet.");
            return SyncResult.skipped("facebook_post_metrics table is not migrated yet — restart the backend to run Flyway.");
        }

        Instant now = Instant.now();
        List<Submission> due = submissionRepository.findDueForFacebookInsights(
                now.minus(lookbackDays, ChronoUnit.DAYS),
                now.minus(refreshIntervalMinutes, ChronoUnit.MINUTES),
                PageRequest.of(0, batchSize));
        int synced = 0;
        int failed = 0;
        String firstError = null;
        for (Submission submission : due) {
            String postId = submission.getPlatformPostId();
            try {
                FacebookPostMetricsSnapshot snapshot = insightsClient.fetchPostMetrics(postId);
                saveSnapshot(submission, snapshot, Instant.now());
                synced++;
            } catch (Exception ex) {
                failed++;
                if (firstError == null) firstError = ex.getMessage();
                log.warn("Facebook insights sync failed for submission {} post {}: {}",
                        submission.getId(), postId, ex.getMessage());
            }
        }
        String reason = null;
        if (due.isEmpty()) {
            reason = "No published posts are due for an insights refresh (need a DASIGConnect-published "
                    + "post within the last " + lookbackDays + " days, not synced in the last "
                    + refreshIntervalMinutes + " minutes).";
        } else if (synced == 0 && failed > 0) {
            reason = "All Graph API fetches failed — check the Page token permissions (read_insights / "
                    + "pages_read_engagement). First error: " + firstError;
        }
        return new SyncResult(due.size(), synced, failed, reason);
    }

    private void saveSnapshot(Submission submission, FacebookPostMetricsSnapshot snapshot, Instant fetchedAt) {
        FacebookPostMetric metric = new FacebookPostMetric();
        metric.setSubmission(submission);
        metric.setFacebookPostId(submission.getPlatformPostId());
        metric.setFetchedAt(fetchedAt);
        metric.setReactions(snapshot.reactions());
        metric.setComments(snapshot.comments());
        metric.setShares(snapshot.shares());
        metric.setReach(snapshot.reach());
        metric.setImpressions(snapshot.impressions());
        metric.setVideoId(snapshot.videoId());
        metric.setPostClicks(snapshot.postClicks());
        metric.setViews(snapshot.views());
        metric.setUniqueViews(snapshot.uniqueViews());
        metric.setFifteenSecondViews(snapshot.fifteenSecondViews());
        metric.setSixtySecondViews(snapshot.sixtySecondViews());
        metric.setWatchTimeMs(snapshot.watchTimeMs());
        metric.setAverageWatchTimeMs(snapshot.averageWatchTimeMs());
        metric.setReactionsByType(snapshot.reactionsByType());
        metric.setRawPost(snapshot.rawPost());
        metric.setRawInsights(snapshot.rawInsights());
        metric.setRawVideoInsights(snapshot.rawVideoInsights());
        metricRepository.save(metric);
    }

    private boolean metricsTableExists() {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.facebook_post_metrics') IS NOT NULL",
                Boolean.class);
        return Boolean.TRUE.equals(exists);
    }
}
