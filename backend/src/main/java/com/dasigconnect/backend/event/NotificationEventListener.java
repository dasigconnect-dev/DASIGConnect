package com.dasigconnect.backend.event;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.dasigconnect.backend.model.entity.NotificationEventType;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.service.EmailService;
import com.dasigconnect.backend.service.NotificationService;

/**
 * Dispatches in-app and email notifications for submission lifecycle events
 * (T2–T7, T9–T17). Each handler runs in its own transaction after the
 * triggering transaction commits, so a notification failure cannot roll back
 * the business action that caused it.
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);
    private static final DateTimeFormatter SLOT_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm 'UTC'");

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public NotificationEventListener(
            NotificationService notificationService,
            UserRepository userRepository,
            EmailService emailService) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    // ── T2 — Submission approved (SCHEDULED) ─────────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSubmissionApproved(SubmissionApprovedEvent event) {
        Submission s = event.submission();
        User contributor = s.getContributor();
        String slot = s.getScheduledAt() != null ? fmt(s.getScheduledAt()) : "TBD";
        String msg = "Your submission '" + s.getEventTitle()
                + "' was approved and is scheduled for " + slot + ".";
        String link = "/submissions/" + s.getId();

        notificationService.createNotification(contributor, NotificationEventType.submission_approved, msg, link);
        sendEmailSilently(contributor.getEmail(),
                "DASIGConnect — Submission approved",
                msg + "\n\nView your scheduled post: " + link);
    }

    // ── T3 — Revision requested (NEEDS_REVISION) ─────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRevisionRequested(RevisionRequestedEvent event) {
        Submission s = event.submission();
        User contributor = s.getContributor();
        String msg = "Revision requested for '" + s.getEventTitle()
                + ".' Review the Validator's remarks.";
        String link = "/submissions/" + s.getId();

        notificationService.createNotification(contributor, NotificationEventType.submission_needs_revision, msg, link);
        String emailBody = msg + "\n\nValidator remarks:\n" + event.remarks()
                + "\n\nView submission: " + link;
        sendEmailSilently(contributor.getEmail(), "DASIGConnect — Revision requested", emailBody);
    }

    // ── T4 — Submission rejected (REJECTED) ──────────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSubmissionRejected(SubmissionRejectedEvent event) {
        Submission s = event.submission();
        User contributor = s.getContributor();
        String msg = "'" + s.getEventTitle() + "' was rejected. See reason for details.";
        String link = "/submissions/" + s.getId();

        notificationService.createNotification(contributor, NotificationEventType.submission_rejected, msg, link);
        String emailBody = msg + "\n\nRejection reason:\n" + event.reason()
                + "\n\nView submission: " + link;
        sendEmailSilently(contributor.getEmail(), "DASIGConnect — Submission rejected", emailBody);
    }

    // ── T5 — Post published (automated, PUBLISHED) ───────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPostPublished(PostPublishedEvent event) {
        Submission s = event.submission();
        String msg = "Your post '" + s.getEventTitle()
                + "' was successfully published to the DASIG Facebook Page. View live post →";
        String link = event.platformPostUrl() != null ? event.platformPostUrl() : "/submissions/" + s.getId();
        notificationService.createNotification(s.getContributor(), NotificationEventType.submission_published, msg, link);
    }

    // ── T6 — Post published (manual, PUBLISHED_MANUAL) ───────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPostPublishedManual(PostPublishedManualEvent event) {
        Submission s = event.submission();
        String postLink = event.platformPostUrl() != null ? event.platformPostUrl() : "/submissions/" + s.getId();

        String contributorMsg = "Your post '" + s.getEventTitle()
                + "' was manually published to the DASIG Facebook Page by the Administrator. View live post →";
        notificationService.createNotification(
                s.getContributor(), NotificationEventType.submission_published_manual, contributorMsg, postLink);

        String validatorMsg = "'" + s.getEventTitle() + "' from "
                + s.getInstitution().getName() + " was manually published by the Administrator.";
        for (User v : validators(s.getInstitution().getId())) {
            notificationService.createNotification(v, NotificationEventType.submission_published_manual, validatorMsg, postLink);
        }
    }

    // ── T7 — Automated publishing failed (PUBLISH_FAILED) ────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPublishFailed(PublishFailedEvent event) {
        Submission s = event.submission();
        String slot = s.getScheduledAt() != null ? fmt(s.getScheduledAt()) : "unknown";
        String link = "/submissions/" + s.getId();

        String adminMsg = "Automated publishing failed for '" + s.getEventTitle()
                + "' (scheduled " + slot + "). Error: " + event.errorDetail()
                + ". Manual action required.";
        for (User admin : admins()) {
            notificationService.createNotification(admin, NotificationEventType.submission_publish_failed, adminMsg, link);
            sendEmailSilently(admin.getEmail(), "DASIGConnect — Publishing failed", adminMsg);
        }

        String contributorMsg = "Your post '" + s.getEventTitle()
                + "' could not be published automatically. The Administrator has been notified.";
        notificationService.createNotification(
                s.getContributor(), NotificationEventType.submission_publish_failed, contributorMsg, link);
    }

    // ── T9 — Override approved ────────────────────────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOverrideApproved(OverrideApprovedEvent event) {
        Submission s = event.submission();
        String link = "/submissions/" + s.getId();

        String contributorMsg = "Your guard rail override request for '" + s.getEventTitle()
                + "' was approved. You may proceed with your selected slot.";
        notificationService.createNotification(event.contributor(), NotificationEventType.override_approved, contributorMsg, link);

        String validatorMsg = "Administrator approved a guard rail override for '"
                + event.contributor().getEmail() + "' — '" + s.getEventTitle() + "'.";
        for (User v : validators(s.getInstitution().getId())) {
            notificationService.createNotification(v, NotificationEventType.override_approved, validatorMsg, link);
        }
    }

    // ── T10 — Override denied ─────────────────────────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOverrideDenied(OverrideDeniedEvent event) {
        Submission s = event.submission();
        String link = "/submissions/" + s.getId();
        String msg = "Your guard rail override request for '" + s.getEventTitle() + "' was not approved.";

        notificationService.createNotification(event.contributor(), NotificationEventType.override_denied, msg, link);
        String emailBody = msg + (event.reason() != null ? "\n\nAdministrator reason: " + event.reason() : "")
                + "\n\nView submission: " + link;
        sendEmailSilently(event.contributor().getEmail(), "DASIGConnect — Override request denied", emailBody);
    }

    // ── T11 — Admin direct post ───────────────────────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAdminDirectPost(AdminDirectPostEvent event) {
        String msg = "The Administrator posted directly to the DASIG Facebook Page on behalf of "
                + event.institution().getName() + ": '"
                + truncate(event.postTitle(), 80) + ".' View post →";
        String link = event.postUrl() != null ? event.postUrl() : "/";
        for (User v : validators(event.institution().getId())) {
            notificationService.createNotification(v, NotificationEventType.admin_direct_post, msg, link);
        }
    }

    // ── T12 — Token expiry warning (GR-T3) ───────────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTokenExpiryWarning(TokenExpiryWarningEvent event) {
        String msg = "The Facebook Page Access Token will expire in " + event.daysUntilExpiry()
                + " days. Re-authenticate the Facebook integration before it expires to avoid publishing interruptions.";
        String link = "/admin/integrations";
        for (User admin : admins()) {
            notificationService.createNotification(admin, NotificationEventType.token_expiring, msg, link);
            sendEmailSilently(admin.getEmail(), "DASIGConnect — Token expiry warning", msg);
        }
    }

    // ── T13 — Token validation failure (GR-T4) ───────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTokenValidationFailed(TokenValidationFailedEvent event) {
        String msg = "CRITICAL: The Facebook Page Access Token failed validation. "
                + "Automated publishing is suspended until the token is reauthorized. "
                + "Re-authenticate immediately in the Resolution Center.";
        String link = "/admin/resolution-center";
        for (User admin : admins()) {
            notificationService.createNotification(admin, NotificationEventType.token_invalid, msg, link);
            sendEmailSilently(admin.getEmail(), "DASIGConnect — CRITICAL: Token validation failed", msg);
        }
    }

    // ── T14 — Institution has no active validator ─────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInstitutionNoValidator(InstitutionNoValidatorEvent event) {
        String name = event.institution().getName();
        String msg = name + " has no active Validators. All pending submissions from this institution "
                + "are being escalated to you until a new Validator is provisioned.";
        String link = "/admin/institutions/" + event.institution().getId();
        for (User admin : admins()) {
            notificationService.createNotification(admin, NotificationEventType.institution_no_validator, msg, link);
            sendEmailSilently(admin.getEmail(), "DASIGConnect — No active Validator at " + name, msg);
        }
    }

    // ── T15 — New institution onboarded ──────────────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInstitutionOnboarded(InstitutionOnboardedEvent event) {
        String name = event.institution().getName();
        String msg = name + " has completed onboarding. "
                + "The Validator account is now active and the workspace is ready.";
        String link = "/admin/institutions/" + event.institution().getId();
        for (User admin : admins()) {
            notificationService.createNotification(admin, NotificationEventType.institution_onboarded, msg, link);
        }
    }

    // ── T16 — Administrator rescheduled a post ────────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSubmissionRescheduled(SubmissionRescheduledEvent event) {
        Submission s = event.submission();
        String msg = "The Administrator rescheduled your post '" + s.getEventTitle()
                + "' from " + fmt(event.originalSlot()) + " to " + fmt(event.newSlot()) + ".";
        String link = "/submissions/" + s.getId();
        notificationService.createNotification(s.getContributor(), NotificationEventType.submission_rescheduled, msg, link);
    }

    // ── T17 — Administrator suggests alternative slot ─────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOverrideSlotSuggested(OverrideSlotSuggestedEvent event) {
        Submission s = event.submission();
        String link = "/submissions/" + s.getId();
        String msg = "The Administrator reviewed your override request for '" + s.getEventTitle()
                + "' and suggests " + fmt(event.suggestedSlot())
                + " as an alternative slot. You may accept this slot, choose a different compliant slot,"
                + " or submit a new override request.";

        notificationService.createNotification(event.contributor(), NotificationEventType.override_slot_suggested, msg, link);
        sendEmailSilently(event.contributor().getEmail(), "DASIGConnect — Alternative slot suggested", msg);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<User> validators(java.util.UUID institutionId) {
        return userRepository.findByInstitutionIdAndRoleOrderByCreatedAtDesc(institutionId, UserRole.validator);
    }

    private List<User> admins() {
        return userRepository.findByRole(UserRole.administrator);
    }

    private void sendEmailSilently(String to, String subject, String body) {
        try {
            emailService.sendPlainText(to, subject, body);
        } catch (Exception ex) {
            log.warn("Email notification to {} failed (in-app notification was already persisted): {}", to, ex.getMessage());
        }
    }

    private static String fmt(Instant instant) {
        return ZonedDateTime.ofInstant(instant, ZoneOffset.UTC).format(SLOT_FMT);
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }
}
