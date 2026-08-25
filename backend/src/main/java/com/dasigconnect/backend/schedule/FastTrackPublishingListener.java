package com.dasigconnect.backend.schedule;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.dasigconnect.backend.event.SubmissionApprovedEvent;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionMediaAsset;
import com.dasigconnect.backend.service.FacebookPublisherService;
import com.dasigconnect.backend.service.PublishingQueryService;

/**
 * UC-3.2 A5: Fast-Track submissions skip the scheduler window and publish
 * immediately after approval commits.
 */
@Component
public class FastTrackPublishingListener {

    private static final Logger log = LoggerFactory.getLogger(FastTrackPublishingListener.class);

    private final PublishingQueryService publishingQueryService;
    private final FacebookPublisherService facebookPublisherService;

    public FastTrackPublishingListener(
            PublishingQueryService publishingQueryService,
            FacebookPublisherService facebookPublisherService) {
        this.publishingQueryService = publishingQueryService;
        this.facebookPublisherService = facebookPublisherService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmissionApproved(SubmissionApprovedEvent event) {
        Submission submission = event.submission();
        if (!submission.isFastTrack() || !facebookPublisherService.isConfigured()) {
            return;
        }

        try {
            Submission claimed = publishingQueryService.claimForPublishing(submission).orElse(null);
            if (claimed == null) {
                log.info("Fast-Track submission {} was already claimed for publishing.", submission.getId());
                return;
            }
            List<SubmissionMediaAsset> mediaLinks =
                    publishingQueryService.loadMediaLinksForSubmission(claimed.getId());
            facebookPublisherService.publishMediaLinks(claimed, mediaLinks);
        } catch (Exception ex) {
            log.error("Fast-Track publishing failed for submission {}: {}",
                    submission.getId(), ex.getMessage(), ex);
        }
    }
}
