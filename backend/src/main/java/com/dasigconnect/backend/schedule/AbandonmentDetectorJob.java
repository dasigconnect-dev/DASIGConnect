package com.dasigconnect.backend.schedule;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.service.ManualPublishingService;

/**
 * Runs every 5 minutes. Clears manual publish sessions that have been open
 * for more than 2 hours without being completed (UC-3.4 abandonment handling).
 */
@Component
public class AbandonmentDetectorJob {

    private static final Logger log = LoggerFactory.getLogger(AbandonmentDetectorJob.class);
    private static final long ABANDONMENT_THRESHOLD_HOURS = 2L;

    private final SubmissionRepository submissionRepository;
    private final ManualPublishingService manualPublishingService;

    public AbandonmentDetectorJob(
            SubmissionRepository submissionRepository,
            ManualPublishingService manualPublishingService) {
        this.submissionRepository = submissionRepository;
        this.manualPublishingService = manualPublishingService;
    }

    @Scheduled(cron = "0 */5 * * * *", zone = "UTC")
    @Transactional
    public void run() {
        try {
            Instant cutoff = Instant.now().minus(ABANDONMENT_THRESHOLD_HOURS, ChronoUnit.HOURS);
            List<Submission> abandoned = submissionRepository.findAbandonedManualPublishes(cutoff);
            if (!abandoned.isEmpty()) {
                log.warn("AbandonmentDetectorJob: clearing {} abandoned manual publish session(s).", abandoned.size());
                for (Submission s : abandoned) {
                    manualPublishingService.clearAbandoned(s);
                }
            }
        } catch (Exception ex) {
            log.error("AbandonmentDetectorJob failed: {}", ex.getMessage(), ex);
        }
    }
}
