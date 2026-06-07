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
import org.springframework.stereotype.Service;

@Service
public class FacebookInsightsSyncService {

    private static final Logger log = LoggerFactory.getLogger(FacebookInsightsSyncService.class);
    private static final int MAX_BATCH_SIZE = 50;

    private final SubmissionRepository submissionRepository;
    private final FacebookPostMetricRepository metricRepository;
    private final FacebookInsightsClient insightsClient;
    private final int batchSize;
    private final int lookbackDays;
    private final int refreshIntervalMinutes;

    public FacebookInsightsSyncService(
            SubmissionRepository submissionRepository,
            FacebookPostMetricRepository metricRepository,
            FacebookInsightsClient insightsClient,
            @Value("${app.facebook.insights.batch-size:20}") int batchSize,
            @Value("${app.facebook.insights.lookback-days:90}") int lookbackDays,
            @Value("${app.facebook.insights.refresh-interval-minutes:360}") int refreshIntervalMinutes) {
        this.submissionRepository = submissionRepository;
        this.metricRepository = metricRepository;
        this.insightsClient = insightsClient;
        this.batchSize = Math.min(Math.max(batchSize, 1), MAX_BATCH_SIZE);
        this.lookbackDays = Math.max(lookbackDays, 1);
        this.refreshIntervalMinutes = Math.max(refreshIntervalMinutes, 15);
    }

    public int syncDueMetrics() {
        if (!insightsClient.isConfigured()) {
            log.info("Facebook insights sync skipped because Facebook token settings are incomplete.");
            return 0;
        }

        Instant now = Instant.now();
        List<Submission> due = submissionRepository.findDueForFacebookInsights(
                now.minus(lookbackDays, ChronoUnit.DAYS),
                now.minus(refreshIntervalMinutes, ChronoUnit.MINUTES),
                PageRequest.of(0, batchSize));
        int synced = 0;
        for (Submission submission : due) {
            String postId = submission.getPlatformPostId();
            try {
                FacebookPostMetricsSnapshot snapshot = insightsClient.fetchPostMetrics(postId);
                saveSnapshot(submission, snapshot, Instant.now());
                synced++;
            } catch (Exception ex) {
                log.warn("Facebook insights sync failed for submission {} post {}: {}",
                        submission.getId(), postId, ex.getMessage());
            }
        }
        return synced;
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
        metric.setRawPost(snapshot.rawPost());
        metric.setRawInsights(snapshot.rawInsights());
        metricRepository.save(metric);
    }
}
