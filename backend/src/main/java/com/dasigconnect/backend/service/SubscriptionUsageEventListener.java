package com.dasigconnect.backend.service;

import com.dasigconnect.backend.event.FastTrackSubmissionEvent;
import com.dasigconnect.backend.event.InstitutionUsageChangedEvent;
import com.dasigconnect.backend.event.MediaStorageUsageChangedEvent;
import com.dasigconnect.backend.event.SubmissionPendingEvent;
import com.dasigconnect.backend.event.UserSeatUsageChangedEvent;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubscriptionUsageEventType;
import com.dasigconnect.backend.model.entity.SubscriptionUsageUnit;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class SubscriptionUsageEventListener {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionUsageEventListener.class);

    private final SubscriptionUsageService usageService;
    private final SubmissionMediaAssetRepository submissionMediaAssetRepository;

    public SubscriptionUsageEventListener(
            SubscriptionUsageService usageService,
            SubmissionMediaAssetRepository submissionMediaAssetRepository) {
        this.usageService = usageService;
        this.submissionMediaAssetRepository = submissionMediaAssetRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmissionPending(SubmissionPendingEvent event) {
        recordSubmission(event.submission());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFastTrackSubmission(FastTrackSubmissionEvent event) {
        recordSubmission(event.submission());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInstitutionChanged(InstitutionUsageChangedEvent event) {
        safely("institution " + event.institutionId(), () -> usageService.recordForOrganization(
                event.organizationId(),
                event.institutionId(),
                null,
                SubscriptionUsageEventType.INSTITUTION,
                event.delta(),
                SubscriptionUsageUnit.COUNT,
                "institution:" + event.institutionId() + ":" + (event.delta() > 0 ? "created" : "deleted"),
                "INSTITUTION",
                event.institutionId(),
                Instant.now()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserSeatChanged(UserSeatUsageChangedEvent event) {
        safely("user seat " + event.userId(), () -> usageService.recordForOrganization(
                event.organizationId(),
                event.institutionId(),
                event.userId(),
                SubscriptionUsageEventType.ACTIVE_USER_SEAT,
                event.delta(),
                SubscriptionUsageUnit.COUNT,
                "active-seat:" + event.userId() + ":" + event.transition(),
                "USER",
                event.userId(),
                Instant.now()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMediaStorageChanged(MediaStorageUsageChangedEvent event) {
        safely("media asset " + event.assetId(), () -> usageService.recordForOrganization(
                event.organizationId(),
                event.institutionId(),
                event.userId(),
                SubscriptionUsageEventType.MEDIA_STORAGE_BYTES,
                event.deltaBytes(),
                SubscriptionUsageUnit.BYTES,
                "media-storage:" + event.assetId() + ":" + event.transition(),
                "MEDIA_ASSET",
                event.assetId(),
                Instant.now()));
    }

    private void recordSubmission(Submission submission) {
        if (submission == null || submission.getId() == null) {
            return;
        }
        safely("submission " + submission.getId(), () -> usageService.recordForSubmission(
                submission.getId(),
                null,
                SubscriptionUsageEventType.SUBMISSION,
                1,
                SubscriptionUsageUnit.COUNT,
                "submission:" + submission.getId(),
                "SUBMISSION",
                submission.getSubmittedAt()));

        for (MediaAsset asset : submissionMediaAssetRepository.findMediaAssetsBySubmissionId(submission.getId())) {
            if (asset == null || asset.getInstitution() == null || asset.getFileSizeBytes() <= 0) {
                continue;
            }
            safely("submission media " + asset.getId(), () -> usageService.recordForOrganization(
                    asset.getInstitution().getOrganizationId(),
                    asset.getInstitution().getId(),
                    asset.getUploader() == null ? null : asset.getUploader().getId(),
                    SubscriptionUsageEventType.MEDIA_STORAGE_BYTES,
                    asset.getFileSizeBytes(),
                    SubscriptionUsageUnit.BYTES,
                    "media-storage:" + asset.getId() + ":uploaded",
                    "MEDIA_ASSET",
                    asset.getId(),
                    asset.getCreatedAt()));
        }
    }

    private void safely(String subject, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.warn("Subscription shadow usage was not recorded for {}: {}", subject, ex.getMessage());
        }
    }
}
