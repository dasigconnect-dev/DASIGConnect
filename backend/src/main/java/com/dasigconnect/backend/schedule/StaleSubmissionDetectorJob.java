package com.dasigconnect.backend.schedule;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dasigconnect.backend.event.PublishFailedEvent;
import com.dasigconnect.backend.event.SubmissionMissedReviewEvent;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.service.SlotReservationService;
import org.springframework.context.ApplicationEventPublisher;

/**
 * GR-T9: Fires every 5 minutes.
 *
 * <ol>
 *   <li>Finds SCHEDULED submissions whose slot has already passed by more than 5
 *       minutes without being picked up by the PublishingSchedulerJob (e.g. the
 *       server was down during the window), transitions them to PUBLISH_FAILED,
 *       and emits {@link PublishFailedEvent}.</li>
 *   <li>Finds PENDING / IN_REVIEW submissions whose scheduled publication time
 *       has passed while still unreviewed (UC-2.4 A6), transitions them to
 *       MISSED_REVIEW, releases the reserved slot, and emits
 *       {@link SubmissionMissedReviewEvent} so administrators are notified.</li>
 * </ol>
 */
@Component
public class StaleSubmissionDetectorJob {

    private static final Logger log = LoggerFactory.getLogger(StaleSubmissionDetectorJob.class);

    private final SubmissionRepository submissionRepository;
    private final SlotReservationService slotReservationService;
    private final ApplicationEventPublisher eventPublisher;

    public StaleSubmissionDetectorJob(
            SubmissionRepository submissionRepository,
            SlotReservationService slotReservationService,
            ApplicationEventPublisher eventPublisher) {
        this.submissionRepository = submissionRepository;
        this.slotReservationService = slotReservationService;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(cron = "0 */5 * * * *", zone = "UTC")
    public void run() {
        Instant cutoff = Instant.now().minus(5, ChronoUnit.MINUTES);

        try {
            List<Submission> missed = findAndMarkFailed(cutoff);
            if (!missed.isEmpty()) {
                log.warn("StaleSubmissionDetectorJob: {} missed submission(s) transitioned to PUBLISH_FAILED.", missed.size());
                for (Submission s : missed) {
                    eventPublisher.publishEvent(new PublishFailedEvent(s, "Publish window missed — server was unavailable during the scheduled time."));
                }
            }
        } catch (Exception ex) {
            log.error("StaleSubmissionDetectorJob (publish-failed sweep) failed: {}", ex.getMessage(), ex);
        }

        try {
            List<Submission> missedReview = findAndMarkMissedReview(cutoff);
            if (!missedReview.isEmpty()) {
                log.warn("StaleSubmissionDetectorJob: {} unreviewed submission(s) transitioned to MISSED_REVIEW.", missedReview.size());
                for (Submission s : missedReview) {
                    eventPublisher.publishEvent(new SubmissionMissedReviewEvent(s));
                }
            }
        } catch (Exception ex) {
            log.error("StaleSubmissionDetectorJob (missed-review sweep) failed: {}", ex.getMessage(), ex);
        }
    }

    @Transactional
    public List<Submission> findAndMarkFailed(Instant cutoff) {
        List<Submission> missed = submissionRepository.findMissedScheduledSubmissions(cutoff);
        missed.removeIf(s -> s.getTokenBlockedAt() != null);
        for (Submission s : missed) {
            boolean isDirectPost = s.getStatus() == SubmissionStatus.direct_post_scheduled
                    || s.getStatus() == SubmissionStatus.direct_post_publishing;
            s.setStatus(isDirectPost ? SubmissionStatus.direct_post_failed : SubmissionStatus.publish_failed);
        }
        submissionRepository.saveAll(missed);
        return missed;
    }

    /**
     * UC-2.4 A6: PENDING / IN_REVIEW submissions whose scheduled publication time
     * has passed are moved to MISSED_REVIEW and their slot reservation is released
     * so the slot is free for reuse. Retry with New Schedule (A8) sends them back
     * to PENDING_APPROVAL.
     */
    @Transactional
    public List<Submission> findAndMarkMissedReview(Instant cutoff) {
        List<Submission> missed = submissionRepository.findMissedReviewSubmissions(cutoff);
        for (Submission s : missed) {
            s.setStatus(SubmissionStatus.missed_review);
            slotReservationService.release(s.getId());
        }
        submissionRepository.saveAll(missed);
        return missed;
    }
}
