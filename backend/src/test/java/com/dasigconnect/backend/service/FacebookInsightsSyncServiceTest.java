package com.dasigconnect.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.entity.FacebookPostMetric;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.repository.FacebookPostMetricRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.service.FacebookInsightsClient.FacebookPostMetricsSnapshot;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

class FacebookInsightsSyncServiceTest {

    @Test
    void syncDueMetrics_skipsWhenClientNotConfigured() {
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        FacebookPostMetricRepository metricRepository = mock(FacebookPostMetricRepository.class);
        FacebookInsightsClient insightsClient = mock(FacebookInsightsClient.class);
        when(insightsClient.isConfigured()).thenReturn(false);

        FacebookInsightsSyncService service = new FacebookInsightsSyncService(
                submissionRepository, metricRepository, insightsClient, 20, 90, 360);

        int synced = service.syncDueMetrics();

        assertEquals(0, synced);
        verify(submissionRepository, never()).findDueForFacebookInsights(any(), any(), any(Pageable.class));
        verify(metricRepository, never()).save(any());
    }

    @Test
    void syncDueMetrics_savesSnapshotForDuePublishedPost() throws Exception {
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        FacebookPostMetricRepository metricRepository = mock(FacebookPostMetricRepository.class);
        FacebookInsightsClient insightsClient = mock(FacebookInsightsClient.class);
        Submission submission = submission("123_456");

        when(insightsClient.isConfigured()).thenReturn(true);
        when(submissionRepository.findDueForFacebookInsights(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(submission));
        when(insightsClient.fetchPostMetrics("123_456")).thenReturn(new FacebookPostMetricsSnapshot(
                11, 4, 2, 120, 180, "{\"id\":\"123_456\"}", "{\"data\":[]}"));

        FacebookInsightsSyncService service = new FacebookInsightsSyncService(
                submissionRepository, metricRepository, insightsClient, 20, 90, 360);

        int synced = service.syncDueMetrics();

        assertEquals(1, synced);
        ArgumentCaptor<FacebookPostMetric> captor = ArgumentCaptor.forClass(FacebookPostMetric.class);
        verify(metricRepository).save(captor.capture());
        FacebookPostMetric saved = captor.getValue();
        assertEquals(submission, saved.getSubmission());
        assertEquals("123_456", saved.getFacebookPostId());
        assertEquals(11, saved.getReactions());
        assertEquals(4, saved.getComments());
        assertEquals(2, saved.getShares());
        assertEquals(120, saved.getReach());
        assertEquals(180, saved.getImpressions());
    }

    @Test
    void syncDueMetrics_continuesAfterSinglePostFailure() throws Exception {
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        FacebookPostMetricRepository metricRepository = mock(FacebookPostMetricRepository.class);
        FacebookInsightsClient insightsClient = mock(FacebookInsightsClient.class);
        Submission failed = submission("bad_post");
        Submission good = submission("good_post");

        when(insightsClient.isConfigured()).thenReturn(true);
        when(submissionRepository.findDueForFacebookInsights(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(failed, good));
        when(insightsClient.fetchPostMetrics("bad_post")).thenThrow(new IOException("permission denied"));
        when(insightsClient.fetchPostMetrics("good_post")).thenReturn(new FacebookPostMetricsSnapshot(
                3, 1, 0, null, null, "{}", null));

        FacebookInsightsSyncService service = new FacebookInsightsSyncService(
                submissionRepository, metricRepository, insightsClient, 20, 90, 360);

        int synced = service.syncDueMetrics();

        assertEquals(1, synced);
        verify(metricRepository).save(any(FacebookPostMetric.class));
    }

    private static Submission submission(String postId) {
        Submission submission = new Submission();
        submission.setId(UUID.randomUUID());
        submission.setStatus(SubmissionStatus.published);
        submission.setPlatformPostId(postId);
        submission.setPublishedAt(Instant.now());
        return submission;
    }
}
