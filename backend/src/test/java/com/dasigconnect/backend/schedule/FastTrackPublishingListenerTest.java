package com.dasigconnect.backend.schedule;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dasigconnect.backend.event.SubmissionApprovedEvent;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.service.FacebookPublisherService;
import com.dasigconnect.backend.service.PublishingQueryService;

@ExtendWith(MockitoExtension.class)
class FastTrackPublishingListenerTest {

    @Mock private PublishingQueryService publishingQueryService;
    @Mock private FacebookPublisherService facebookPublisherService;

    private FastTrackPublishingListener listener;

    @BeforeEach
    void setUp() {
        listener = new FastTrackPublishingListener(publishingQueryService, facebookPublisherService);
    }

    @Test
    void approvedStandardSubmission_isIgnored() {
        Submission submission = submission(false);

        listener.onSubmissionApproved(new SubmissionApprovedEvent(submission));

        verifyNoInteractions(publishingQueryService, facebookPublisherService);
    }

    @Test
    void approvedFastTrackWithoutFacebook_isMarkedFailedInsteadOfRemainingStuck() {
        Submission submission = submission(true);
        when(facebookPublisherService.isConfigured()).thenReturn(false);

        listener.onSubmissionApproved(new SubmissionApprovedEvent(submission));

        verify(facebookPublisherService).markFailed(
                submission,
                "Fast-Track publishing is unavailable because Facebook is not configured.");
        verifyNoInteractions(publishingQueryService);
    }

    @Test
    void approvedFastTrackWithFacebook_isClaimedAndPublished() {
        Submission submission = submission(true);
        when(facebookPublisherService.isConfigured()).thenReturn(true);
        when(publishingQueryService.claimForPublishing(submission)).thenReturn(Optional.of(submission));
        when(publishingQueryService.loadMediaLinksForSubmission(submission.getId())).thenReturn(java.util.List.of());

        listener.onSubmissionApproved(new SubmissionApprovedEvent(submission));

        verify(facebookPublisherService).publishMediaLinks(submission, java.util.List.of());
        verify(facebookPublisherService, never()).markFailed(submission, "Fast-Track publishing failed");
    }

    private static Submission submission(boolean fastTrack) {
        Submission submission = new Submission();
        submission.setId(UUID.randomUUID());
        submission.setFastTrack(fastTrack);
        return submission;
    }
}
