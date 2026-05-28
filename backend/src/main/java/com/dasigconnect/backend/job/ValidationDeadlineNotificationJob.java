package com.dasigconnect.backend.job;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dasigconnect.backend.model.entity.NotificationEventType;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.repository.NotificationRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.service.EmailDeliveryService;
import com.dasigconnect.backend.service.NotificationService;

/**
 * T8 — Fires every 5 minutes. For each PENDING/IN_REVIEW submission whose
 * scheduled_at is within the next 30 minutes, sends an URGENT notification to
 * all validators at the institution and to all administrators. A dedup check
 * prevents the same alert from being sent twice within any 30-minute window.
 */
@Component
public class ValidationDeadlineNotificationJob {

    private static final Logger log = LoggerFactory.getLogger(ValidationDeadlineNotificationJob.class);
    private static final DateTimeFormatter SLOT_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm 'UTC'");

    private final SubmissionRepository submissionRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailDeliveryService emailDeliveryService;

    public ValidationDeadlineNotificationJob(
            SubmissionRepository submissionRepository,
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            EmailDeliveryService emailDeliveryService) {
        this.submissionRepository = submissionRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.emailDeliveryService = emailDeliveryService;
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void checkValidationDeadlines() {
        Instant now = Instant.now();
        Instant windowEnd = now.plusSeconds(30 * 60);

        List<Submission> urgent = submissionRepository.findApproachingDeadlines(now, windowEnd);
        if (urgent.isEmpty()) {
            return;
        }

        log.info("T8 deadline check: {} submission(s) approaching publication without validation.", urgent.size());

        List<User> admins = userRepository.findByRole(UserRole.administrator);

        for (Submission s : urgent) {
            String link = "/submissions/" + s.getId();
            String msg = "URGENT: '" + s.getEventTitle()
                    + "' is scheduled to publish in less than 30 minutes and has not been validated. "
                    + "Immediate action required.";

            List<User> validators = userRepository
                    .findByInstitutionIdAndRoleOrderByCreatedAtDesc(s.getInstitution().getId(), UserRole.validator);

            for (User v : validators) {
                if (alreadyNotified(v, link)) continue;
                notificationService.createNotification(v, NotificationEventType.validation_timeout, msg, link);
                emailDeliveryService.send(v,
                        NotificationEventType.validation_timeout.name(),
                        "DASIGConnect — URGENT: Validation deadline approaching",
                        msg);
            }
            for (User admin : admins) {
                if (alreadyNotified(admin, link)) continue;
                notificationService.createNotification(admin, NotificationEventType.validation_timeout, msg, link);
                emailDeliveryService.send(admin,
                        NotificationEventType.validation_timeout.name(),
                        "DASIGConnect — URGENT: Validation deadline approaching",
                        msg);
            }
        }
    }

    private boolean alreadyNotified(User recipient, String deepLink) {
        Instant since = Instant.now().minusSeconds(30 * 60);
        return notificationRepository.existsByRecipientIdAndEventTypeAndDeepLinkAndCreatedAtAfter(
                recipient.getId(), NotificationEventType.validation_timeout, deepLink, since);
    }

    static String fmt(Instant instant) {
        return ZonedDateTime.ofInstant(instant, ZoneOffset.UTC).format(SLOT_FMT);
    }
}
