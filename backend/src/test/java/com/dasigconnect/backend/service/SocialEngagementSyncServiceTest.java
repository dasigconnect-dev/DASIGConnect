package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionEngagementMetric;
import com.dasigconnect.backend.repository.SubmissionEngagementMetricRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.service.FacebookEngagementAnalyticsClient.PostEngagement;

@ExtendWith(MockitoExtension.class)
class SocialEngagementSyncServiceTest {

    @Mock
    private SubmissionEngagementMetricRepository engagementMetricRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private FacebookEngagementAnalyticsClient facebookClient;

    private SocialEngagementSyncService service;

    @BeforeEach
    void setUp() {
        service = new SocialEngagementSyncService(
                engagementMetricRepository,
                submissionRepository,
                facebookClient,
                25);
    }

    @Test
    void syncPending_withMissingEngagement_processesBatchAndReturnsCount() throws Exception {
        UUID sub1 = UUID.randomUUID();
        UUID sub2 = UUID.randomUUID();

        when(engagementMetricRepository.findPublishedSubmissionIdsMissingEngagement(PageRequest.of(0, 25)))
                .thenReturn(List.of(sub1, sub2));

        Submission s1 = new Submission();
        s1.setId(sub1);
        s1.setPlatformPostId("12345_67890");

        Submission s2 = new Submission();
        s2.setId(sub2);
        s2.setPlatformPostId(null); // No platform post id -> fails syncOne

        when(submissionRepository.findById(sub1)).thenReturn(Optional.of(s1));
        when(submissionRepository.findById(sub2)).thenReturn(Optional.of(s2));

        when(engagementMetricRepository.findBySubmissionId(sub1)).thenReturn(Optional.empty());
        when(facebookClient.fetchPostEngagement("12345_67890"))
                .thenReturn(new PostEngagement(150L, 25L, 5L, 2L));

        int synced = service.syncPending();

        assertThat(synced).isEqualTo(1);
    }

    @Test
    void syncOne_successfulFetch_savesUpdatedMetrics() throws Exception {
        UUID subId = UUID.randomUUID();
        Submission submission = new Submission();
        submission.setId(subId);
        submission.setPlatformPostId("fb_post_999");

        when(submissionRepository.findById(subId)).thenReturn(Optional.of(submission));
        when(engagementMetricRepository.findBySubmissionId(subId)).thenReturn(Optional.empty());
        when(facebookClient.fetchPostEngagement("fb_post_999"))
                .thenReturn(new PostEngagement(500L, 100L, 20L, 10L));

        boolean result = service.syncOne(subId);

        assertThat(result).isTrue();

        ArgumentCaptor<SubmissionEngagementMetric> captor = ArgumentCaptor.forClass(SubmissionEngagementMetric.class);
        verify(engagementMetricRepository).save(captor.capture());
        SubmissionEngagementMetric saved = captor.getValue();

        assertThat(saved.getReach()).isEqualTo(500L);
        assertThat(saved.getReactions()).isEqualTo(100L);
        assertThat(saved.getCommentsCount()).isEqualTo(20L);
        assertThat(saved.getShares()).isEqualTo(10L);
        assertThat(saved.getFetchedAt()).isNotNull();
        assertThat(saved.getLastError()).isNull();
    }

    @Test
    void syncOne_apiThrowsException_recordsLastErrorAndReturnsFalse() throws Exception {
        UUID subId = UUID.randomUUID();
        Submission submission = new Submission();
        submission.setId(subId);
        submission.setPlatformPostId("fb_post_error");

        when(submissionRepository.findById(subId)).thenReturn(Optional.of(submission));
        when(engagementMetricRepository.findBySubmissionId(subId)).thenReturn(Optional.empty());
        when(facebookClient.fetchPostEngagement("fb_post_error"))
                .thenThrow(new IOException("Facebook Graph API rate limit exceeded"));

        boolean result = service.syncOne(subId);

        assertThat(result).isFalse();

        ArgumentCaptor<SubmissionEngagementMetric> captor = ArgumentCaptor.forClass(SubmissionEngagementMetric.class);
        verify(engagementMetricRepository).save(captor.capture());
        SubmissionEngagementMetric saved = captor.getValue();

        assertThat(saved.getLastError()).contains("Facebook Graph API rate limit exceeded");
        assertThat(saved.getLastAttemptAt()).isNotNull();
    }

    @Test
    void syncOne_missingSubmission_returnsFalse() {
        UUID subId = UUID.randomUUID();
        when(submissionRepository.findById(subId)).thenReturn(Optional.empty());

        boolean result = service.syncOne(subId);

        assertThat(result).isFalse();
    }

    @Test
    void constructor_clampsBatchSizeBetween1And100() {
        SocialEngagementSyncService lowService = new SocialEngagementSyncService(
                engagementMetricRepository, submissionRepository, facebookClient, -10);
        SocialEngagementSyncService highService = new SocialEngagementSyncService(
                engagementMetricRepository, submissionRepository, facebookClient, 500);

        when(engagementMetricRepository.findPublishedSubmissionIdsMissingEngagement(PageRequest.of(0, 1)))
                .thenReturn(List.of());
        when(engagementMetricRepository.findPublishedSubmissionIdsMissingEngagement(PageRequest.of(0, 100)))
                .thenReturn(List.of());

        lowService.syncPending();
        verify(engagementMetricRepository).findPublishedSubmissionIdsMissingEngagement(PageRequest.of(0, 1));

        highService.syncPending();
        verify(engagementMetricRepository).findPublishedSubmissionIdsMissingEngagement(PageRequest.of(0, 100));
    }
}
