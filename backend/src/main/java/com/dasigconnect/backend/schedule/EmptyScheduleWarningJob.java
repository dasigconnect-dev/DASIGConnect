package com.dasigconnect.backend.schedule;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dasigconnect.backend.event.EmptyScheduleEvent;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.InstitutionStatus;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.service.ContentIdeaSuggestionService;

/**
 * T-07: Weekly scanner for empty upcoming posting schedules.
 * Scans active institutions; if 0 posts are scheduled in the next 7 days,
 * generates AI topic suggestions and publishes EmptyScheduleEvent.
 */
@Component
public class EmptyScheduleWarningJob {

    private static final Logger log = LoggerFactory.getLogger(EmptyScheduleWarningJob.class);

    private final InstitutionRepository institutionRepository;
    private final SubmissionRepository submissionRepository;
    private final ContentIdeaSuggestionService contentIdeaSuggestionService;
    private final ApplicationEventPublisher eventPublisher;

    public EmptyScheduleWarningJob(
            InstitutionRepository institutionRepository,
            SubmissionRepository submissionRepository,
            ContentIdeaSuggestionService contentIdeaSuggestionService,
            ApplicationEventPublisher eventPublisher) {
        this.institutionRepository = institutionRepository;
        this.submissionRepository = submissionRepository;
        this.contentIdeaSuggestionService = contentIdeaSuggestionService;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(cron = "${app.schedule.empty-schedule-cron:0 0 8 * * MON}")
    public void scanEmptySchedules() {
        log.info("Running EmptyScheduleWarningJob scan for active institutions");
        List<Institution> institutions = institutionRepository.findAllByStatus(InstitutionStatus.active);
        Instant now = Instant.now();
        Instant upcomingWindow = now.plus(7, ChronoUnit.DAYS);

        for (Institution inst : institutions) {
            try {
                long count = submissionRepository.countUpcomingScheduledByInstitution(inst.getId(), now, upcomingWindow);
                if (count == 0) {
                    log.info("Institution {} has 0 scheduled posts in next 7 days — generating T-07 alert", inst.getName());
                    List<String> suggestions = contentIdeaSuggestionService.generateSuggestions(inst);
                    eventPublisher.publishEvent(new EmptyScheduleEvent(inst, suggestions));
                }
            } catch (Exception ex) {
                log.warn("Error checking schedule for institution {}: {}", inst.getName(), ex.getMessage());
            }
        }
    }
}
