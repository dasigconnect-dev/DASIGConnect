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
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.repository.SubmissionRepository;
import org.springframework.context.ApplicationEventPublisher;

/**
 * GR-T9: Fires every 5 minutes. Finds SCHEDULED submissions whose slot has
 * already passed by more than 5 minutes without being picked up by the
 * PublishingSchedulerJob (e.g. server was down during the window).
 *
 * Transitions those submissions to PUBLISH_FAILED and emits PublishFailedEvent
 * so the NotificationEventListener notifies the administrator.
 */
@Component
public class StaleSubmissionDetectorJob {

    private static final Logger log = LoggerFactory.getLogger(StaleSubmissionDetectorJob.class);

    private final SubmissionRepository submissionRepository;
    private final ApplicationEventPublisher eventPublisher;

    public StaleSubmissionDetectorJob(
            SubmissionRepository submissionRepository,
            ApplicationEventPublisher eventPublisher) {
        this.submissionRepository = submissionRepository;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(cron = "0 */5 * * * *", zone = "UTC")
    public void run() {
        try {
            Instant cutoff = Instant.now().minus(5, ChronoUnit.MINUTES);
            List<Submission> missed = findAndMarkFailed(cutoff);
            if (!missed.isEmpty()) {
                log.warn("StaleSubmissionDetectorJob: {} missed submission(s) transitioned to PUBLISH_FAILED.", missed.size());
                for (Submission s : missed) {
                    eventPublisher.publishEvent(new PublishFailedEvent(s, "Publish window missed — server was unavailable during the scheduled time."));
                }
            }
        } catch (Exception ex) {
            log.error("StaleSubmissionDetectorJob failed: {}", ex.getMessage(), ex);
        }
    }

    @Transactional
    public List<Submission> findAndMarkFailed(Instant cutoff) {
        List<Submission> missed = submissionRepository.findMissedScheduledSubmissions(cutoff);
        for (Submission s : missed) {
            s.setStatus(SubmissionStatus.publish_failed);
        }
        submissionRepository.saveAll(missed);
        return missed;
    }
}
