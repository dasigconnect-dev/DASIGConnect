package com.dasigconnect.backend.job;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.service.ManualPublishingService;

/**
 * UC-3.4 A2 — Workflow Abandoned.
 * Detects and clears manual publish sessions that have been open for more than 2 hours
 * without the administrator clicking Mark as Published or Cancel.
 */
@Component
public class AbandonmentDetectorJob {

    private static final Logger log = LoggerFactory.getLogger(AbandonmentDetectorJob.class);
    private static final long ABANDONMENT_SECONDS = 2 * 3600;

    private final SubmissionRepository submissionRepository;
    private final ManualPublishingService manualPublishingService;

    public AbandonmentDetectorJob(
            SubmissionRepository submissionRepository,
            ManualPublishingService manualPublishingService) {
        this.submissionRepository = submissionRepository;
        this.manualPublishingService = manualPublishingService;
    }

    @Scheduled(fixedDelay = 300_000) // every 5 minutes
    public void detectAndClearAbandoned() {
        Instant cutoff = Instant.now().minusSeconds(ABANDONMENT_SECONDS);
        List<Submission> abandoned = submissionRepository.findAbandonedManualPublishes(cutoff);
        if (!abandoned.isEmpty()) {
            log.info("AbandonmentDetectorJob: clearing {} abandoned manual publish session(s).", abandoned.size());
            abandoned.forEach(manualPublishingService::clearAbandoned);
        }
    }
}
