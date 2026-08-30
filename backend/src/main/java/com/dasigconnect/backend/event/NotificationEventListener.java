package com.dasigconnect.backend.event;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import com.dasigconnect.backend.service.EmailDeliveryService;
import com.dasigconnect.backend.service.MessengerDeliveryService;
import com.dasigconnect.backend.service.NotificationService;

/**
 * Dispatches in-app, email, and Facebook Messenger notifications for system
 * lifecycle events (T-01 through T-12, plus operational events). Each handler
 * runs in its own transaction after the triggering transaction commits
 * (AFTER_COMMIT / REQUIRES_NEW), ensuring notification delivery never blocks or
 * rolls back core business transactions.
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private static final DateTimeFormatter SLOT_FMT
            = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm 'UTC'");

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final EmailDeliveryService emailDeliveryService;
    private final MessengerDeliveryService messengerDeliveryService;
    private final String frontendBaseUrl;

    public NotificationEventListener(
            NotificationService notificationService,
            UserRepository userRepository,
            EmailDeliveryService emailDeliveryService,
            MessengerDeliveryService messengerDeliveryService,
            @Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.emailDeliveryService = emailDeliveryService;
        this.messengerDeliveryService = messengerDeliveryService;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    // ── T-01 — New Draft Submitted (DRAFT → PENDING) ──────────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSubmissionPending(SubmissionPendingEvent event) {
        Submission s = event.submission();
        String contributorEmail = s.getContributor() != null ? s.getContributor().getEmail() : "A contributor";
        String scheduledPart = s.getScheduledAt() != null ? " — scheduled for " + fmt(s.getScheduledAt()) : "";
        String msg = contributorEmail + " submitted '" + s.getEventTitle() + "' for approval" + scheduledPart + ".";
        String link = "/submissions/" + s.getId();

        List<User> admins = allModerators();
        for (User admin : admins) {
            notificationService.createNotification(admin, NotificationEventType.submission_pending, msg, link);
            // Messenger delivery (A4 / A5)
            String messengerMsg = "New submission awaiting validation: \"" + s.getEventTitle()
                    + "\". Open DASIGConnect: " + frontendBaseUrl + link;
            messengerDeliveryService.sendToUser(admin.getId(), messengerMsg);
        }
    }

    // ── T-02 — Post Approved & Scheduled (PENDING → SCHEDULED) ────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSubmissionApproved(SubmissionApprovedEvent event) {
        Submission s = event.submission();
        User contributor = s.getContributor();
        String slot = s.getScheduledAt() != null ? fmt(s.getScheduledAt()) : "TBD";
        String link = "/submissions/" + s.getId();
        String msg = "Your submission '" + s.getEventTitle()
                + "' was approved and is scheduled for " + slot + ".";
        if (event.edited()) {
            msg += " The Moderator made changes before publishing — view the diff at " + link + ".";
        }

        notificationService.createNotification(contributor, NotificationEventType.submission_approved, msg, link);
        emailDeliveryService.send(contributor,
                NotificationEventType.submission_approved.name(),
                "DASIGConnect — Submission approved",
                msg + "\n\nView your scheduled post: " + frontendBaseUrl + link);
    }

    // ── T-03 — Post Rejected (PENDING → REJECTED) ─────────────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSubmissionRejected(SubmissionRejectedEvent event) {
        Submission s = event.submission();
        User contributor = s.getContributor();
        String msg = "'" + s.getEventTitle() + "' was rejected. See reason for details.";
        String link = "/submissions/" + s.getId();

        notificationService.createNotification(contributor, NotificationEventType.submission_rejected, msg, link);
        String emailBody = msg + "\n\nRejection reason:\n" + event.reason()
                + "\n\nView submission: " + frontendBaseUrl + link;
        emailDeliveryService.send(contributor,
                NotificationEventType.submission_rejected.name(),
                "DASIGConnect — Submission rejected",
                emailBody);
    }

    // ── Revision Requested (PENDING → NEEDS_REVISION) ─────────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRevisionRequested(RevisionRequestedEvent event) {
        Submission s = event.submission();
        User contributor = s.getContributor();
        String msg = "Revision requested for '" + s.getEventTitle()
                + ".' Review the Moderator's remarks.";
        String link = "/submissions/" + s.getId();

        notificationService.createNotification(contributor, NotificationEventType.submission_needs_revision, msg, link);
        String emailBody = msg + "\n\nModerator remarks:\n" + event.remarks()
                + "\n\nView submission: " + frontendBaseUrl + link;
        emailDeliveryService.send(contributor,
                NotificationEventType.submission_needs_revision.name(),
                "DASIGConnect — Revision requested",
                emailBody);
    }

    // ── T-04 — Post Published (Automated via Graph API) ───────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPostPublished(PostPublishedEvent event) {
        Submission s = event.submission();
        String msg = "Your post '" + s.getEventTitle()
                + "' was successfully published to the DASIG Facebook Page. View live post →";
        String link = event.platformPostUrl() != null ? event.platformPostUrl() : "/submissions/" + s.getId();
        notificationService.createNotification(s.getContributor(), NotificationEventType.submission_published, msg, link);
    }

    // ── T-05 — Post Published (Manual Fallback) ───────────────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPostPublishedManual(PostPublishedManualEvent event) {
        Submission s = event.submission();
        String postLink = event.platformPostUrl() != null ? event.platformPostUrl() : "/submissions/" + s.getId();

        String contributorMsg = "Your post '" + s.getEventTitle()
                + "' was manually published to the DASIG Facebook Page by the Moderator. View live post →";
        notificationService.createNotification(
                s.getContributor(), NotificationEventType.submission_published_manual, contributorMsg, postLink);

        String adminMsg = "'" + s.getEventTitle() + "' from "
                + s.getInstitution().getName() + " was manually published by the Moderator.";
        for (User admin : allModerators()) {
            notificationService.createNotification(admin, NotificationEventType.submission_published_manual, adminMsg, postLink);
        }
    }

    // ── T-06 — Automated Publishing Failed (PUBLISH_FAILED) ───────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPublishFailed(PublishFailedEvent event) {
        Submission s = event.submission();
        String slot = s.getScheduledAt() != null ? fmt(s.getScheduledAt()) : "unknown";
        String link = "/submissions/" + s.getId();

        String adminMsg = "Automated publishing failed for '" + s.getEventTitle()
                + "' (scheduled " + slot + "). Error: " + event.errorDetail()
                + ". Manual action required.";

        // Notify super admins and moderators (both network-wide roles)
        List<User> targetAdmins = new java.util.ArrayList<>(superAdmins());
        for (User ia : allModerators()) {
            if (!targetAdmins.contains(ia)) {
                targetAdmins.add(ia);
            }
        }

        for (User admin : targetAdmins) {
            notificationService.createNotification(admin, NotificationEventType.submission_publish_failed, adminMsg, link);
            emailDeliveryService.send(admin,
                    NotificationEventType.submission_publish_failed.name(),
                    "DASIGConnect — Publishing failed",
                    adminMsg + "\n\nResolution Center: " + frontendBaseUrl + "/admin/resolution");
            // Messenger alert (A4 / A5)
            String messengerAlert = "URGENT: Automated publishing failed for \"" + s.getEventTitle()
                    + "\". Manual action required: " + frontendBaseUrl + link;
            messengerDeliveryService.sendToUser(admin.getId(), messengerAlert);
        }

        String contributorMsg = "Your post '" + s.getEventTitle()
                + "' could not be published automatically. The Moderator has been notified.";
        notificationService.createNotification(
                s.getContributor(), NotificationEventType.submission_publish_failed, contributorMsg, link);
    }

    // ── UC-2.4 A6 — Submission missed its review window (MISSED_REVIEW) ───────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSubmissionMissedReview(SubmissionMissedReviewEvent event) {
        Submission s = event.submission();
        String slot = s.getScheduledAt() != null ? fmt(s.getScheduledAt()) : "unknown";
        String link = "/submissions/" + s.getId();

        String adminMsg = "'" + s.getEventTitle() + "' missed its scheduled publication time ("
                + slot + ") without review. Its slot has been released — assign a new schedule to"
                + " send it back to the approval queue.";
        for (User admin : superAdmins()) {
            notificationService.createNotification(admin, NotificationEventType.submission_missed_review, adminMsg, link);
            emailDeliveryService.send(admin,
                    NotificationEventType.submission_missed_review.name(),
                    "DASIGConnect — Submission missed its review window",
                    adminMsg);
        }

        String contributorMsg = "Your submission '" + s.getEventTitle()
                + "' missed its scheduled slot before it could be reviewed. An Moderator will"
                + " reschedule it for a fresh review.";
        notificationService.createNotification(
                s.getContributor(), NotificationEventType.submission_missed_review, contributorMsg, link);
    }

    // ── T-07 — Empty Schedule Warning ─────────────────────────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEmptySchedule(EmptyScheduleEvent event) {
        String instName = event.institution().getName();
        StringBuilder sb = new StringBuilder();
        sb.append("Upcoming schedule for ").append(instName).append(" is currently empty (0 posts scheduled).");
        if (event.suggestions() != null && !event.suggestions().isEmpty()) {
            sb.append("\n\nContent suggestions:");
            for (String suggestion : event.suggestions()) {
                sb.append("\n• ").append(suggestion);
            }
        }
        String msg = sb.toString();
        String link = "/scheduler/calendar";

        // In-app to institution admins
        for (User admin : allModerators()) {
            notificationService.createNotification(admin, NotificationEventType.empty_schedule_warning, msg, link);
        }
        // In-app to institution contributors
        for (User contributor : institutionContributors(event.institution().getId())) {
            notificationService.createNotification(contributor, NotificationEventType.empty_schedule_warning, msg, link);
        }
    }

    // ── T-08 — Token Expiry Warning (GR-T3) ───────────────────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTokenExpiryWarning(TokenExpiryWarningEvent event) {
        String msg = "The Facebook Page Access Token will expire in " + event.daysUntilExpiry()
                + " days. Re-authenticate the Facebook integration before it expires to avoid publishing interruptions.";
        String link = "/admin/resolution";
        for (User admin : superAdmins()) {
            notificationService.createNotification(admin, NotificationEventType.token_expiring, msg, link);
            emailDeliveryService.send(admin,
                    NotificationEventType.token_expiring.name(),
                    "DASIGConnect — Token expiry warning",
                    msg + "\n\nManage tokens: " + frontendBaseUrl + link);
        }
    }

    // ── T-09 — Token Validation Failure (GR-T4) ───────────────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTokenValidationFailed(TokenValidationFailedEvent event) {
        String msg = "CRITICAL: The Facebook Page Access Token failed validation. "
                + "Automated publishing is suspended until the token is reauthorized. "
                + "Re-authenticate immediately in the Resolution Center.";
        String link = "/admin/resolution";
        for (User admin : superAdmins()) {
            notificationService.createNotification(admin, NotificationEventType.token_invalid, msg, link);
            emailDeliveryService.send(admin,
                    NotificationEventType.token_invalid.name(),
                    "DASIGConnect — CRITICAL: Token validation failed",
                    msg + "\n\nResolution Center: " + frontendBaseUrl + link);
        }
    }

    // ── Token Publishing Suspended Alert (Suspension Lifecycle) ───────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTokenPublishingSuspended(TokenPublishingSuspendedEvent event) {
        Submission s = event.submission();
        String link = "/submissions/" + s.getId();
        String msg = switch (event.stage()) {
            case FIRST_ALERT ->
                "Automated publishing for '" + s.getEventTitle()
                + "' is suspended because the Facebook Page Access Token expired. "
                + "The post remains scheduled while the token is reauthorized.";
            case ESCALATION_24H ->
                "Escalation: '" + s.getEventTitle()
                + "' has been blocked by an expired Facebook Page Access Token for 24 hours. "
                + "Reauthorize the token to resume automated publishing.";
            case FINAL_FAILURE ->
                "Final alert: '" + s.getEventTitle()
                + "' has been blocked by an expired Facebook Page Access Token for 48 hours. "
                + "The submission was moved to Publish Failed for manual recovery.";
        };
        if (event.detail() != null && !event.detail().isBlank()) {
            msg = msg + " Detail: " + event.detail();
        }
        for (User admin : superAdmins()) {
            notificationService.createNotification(admin, NotificationEventType.token_invalid, msg, link);
            emailDeliveryService.send(admin,
                    NotificationEventType.token_invalid.name(),
                    "DASIGConnect - Facebook token publishing alert",
                    msg + "\n\nResolution Center: " + frontendBaseUrl + link);
        }
    }

    // ── T-10 — Moderator rescheduled a post ────────────────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSubmissionRescheduled(SubmissionRescheduledEvent event) {
        Submission s = event.submission();
        String msg = "The Moderator rescheduled your post '" + s.getEventTitle()
                + "' from " + fmt(event.originalSlot()) + " to " + fmt(event.newSlot()) + ".";
        String link = "/submissions/" + s.getId();
        notificationService.createNotification(s.getContributor(), NotificationEventType.submission_rescheduled, msg, link);
    }

    // ── T-11 — Fast-Track Live Event Submission ───────────────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFastTrackSubmission(FastTrackSubmissionEvent event) {
        Submission s = event.submission();
        String contributorEmail = s.getContributor() != null ? s.getContributor().getEmail() : "A contributor";
        String msg = "URGENT Fast-Track live event submission: " + contributorEmail + " submitted '"
                + s.getEventTitle() + "' for immediate approval.";
        String link = "/submissions/" + s.getId();

        List<User> admins = allModerators();
        for (User admin : admins) {
            notificationService.createNotification(admin, NotificationEventType.fast_track_submission, msg, link);
            emailDeliveryService.send(admin,
                    NotificationEventType.fast_track_submission.name(),
                    "URGENT: Fast-Track submission needs approval",
                    msg + "\n\nOpen DASIGConnect: " + frontendBaseUrl + link);
            // Messenger alert (A4 / A5)
            String messengerMsg = "URGENT Fast-Track submission: \"" + s.getEventTitle()
                    + "\". Open DASIGConnect: " + frontendBaseUrl + link;
            messengerDeliveryService.sendToUser(admin.getId(), messengerMsg);
        }
    }

    // ── T-12 — Embedding Reconciliation Failure Digest ────────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEmbeddingFailureDigest(EmbeddingFailureDigestEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("Weekly AI Embedding Digest: ").append(event.failedCount())
                .append(" media asset(s) failed embedding generation.");
        if (event.sampleFilenames() != null && !event.sampleFilenames().isEmpty()) {
            sb.append(" Affected files: ").append(String.join(", ", event.sampleFilenames()));
        }
        String msg = sb.toString();
        String link = "/media-repository";

        for (User admin : superAdmins()) {
            notificationService.createNotification(admin, NotificationEventType.embedding_failure_digest, msg, link);
        }
    }

    // ── Additional Operational Events ─────────────────────────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOverrideApproved(OverrideApprovedEvent event) {
        Submission s = event.submission();
        String link = "/submissions/" + s.getId();

        String contributorMsg = "Your guard rail override request for '" + s.getEventTitle()
                + "' was approved. You may proceed with your selected slot.";
        notificationService.createNotification(event.contributor(), NotificationEventType.override_approved, contributorMsg, link);

        String adminMsg = "Moderator approved a guard rail override for '"
                + event.contributor().getEmail() + "' — '" + s.getEventTitle() + "'.";
        for (User admin : allModerators()) {
            notificationService.createNotification(admin, NotificationEventType.override_approved, adminMsg, link);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOverrideDenied(OverrideDeniedEvent event) {
        Submission s = event.submission();
        String link = "/submissions/" + s.getId();
        String msg = "Your guard rail override request for '" + s.getEventTitle() + "' was not approved.";

        notificationService.createNotification(event.contributor(), NotificationEventType.override_denied, msg, link);
        String emailBody = msg + (event.reason() != null ? "\n\nModerator reason: " + event.reason() : "")
                + "\n\nView submission: " + frontendBaseUrl + link;
        emailDeliveryService.send(event.contributor(),
                NotificationEventType.override_denied.name(),
                "DASIGConnect — Override request denied",
                emailBody);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOverrideSlotSuggested(OverrideSlotSuggestedEvent event) {
        Submission s = event.submission();
        String link = "/submissions/" + s.getId();
        String msg = "The Moderator reviewed your override request for '" + s.getEventTitle()
                + "' and suggests " + fmt(event.suggestedSlot())
                + " as an alternative slot. You may accept this slot, choose a different compliant slot,"
                + " or submit a new override request.";

        notificationService.createNotification(event.contributor(), NotificationEventType.override_slot_suggested, msg, link);
        emailDeliveryService.send(event.contributor(),
                NotificationEventType.override_slot_suggested.name(),
                "DASIGConnect — Alternative slot suggested",
                msg + "\n\nView submission: " + frontendBaseUrl + link);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAdminDirectPost(AdminDirectPostEvent event) {
        String msg = "The Moderator posted directly to the DASIG Facebook Page on behalf of "
                + event.institution().getName() + ": '"
                + truncate(event.postTitle(), 80) + ".' View post →";
        String link = event.postUrl() != null ? event.postUrl() : "/";
        for (User admin : allModerators()) {
            notificationService.createNotification(admin, NotificationEventType.admin_direct_post, msg, link);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInstitutionNoValidator(InstitutionNoValidatorEvent event) {
        String name = event.institution().getName();
        String msg = name + " has no active Moderators. All pending submissions from this institution "
                + "are being escalated until an Moderator is provisioned.";
        String link = "/admin/institution-management";
        for (User admin : superAdmins()) {
            notificationService.createNotification(admin, NotificationEventType.institution_no_validator, msg, link);
            emailDeliveryService.send(admin,
                    NotificationEventType.institution_no_validator.name(),
                    "DASIGConnect — No active Moderator at " + name,
                    msg + "\n\nManage institutions: " + frontendBaseUrl + link);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInstitutionOnboarded(InstitutionOnboardedEvent event) {
        String name = event.institution().getName();
        String msg = name + " has completed onboarding. "
                + "The Moderator account is now active and the workspace is ready.";
        String link = "/admin/institution-management";
        for (User admin : superAdmins()) {
            notificationService.createNotification(admin, NotificationEventType.institution_onboarded, msg, link);
        }
    }

    // ── T18 — Account role changed (promotion / demotion) ─────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserRoleChanged(UserRoleChangedEvent event) {
        User user = event.user();
        if (user == null) {
            return;
        }
        String msg = "Your account role changed from " + roleLabel(event.fromRole())
                + " to " + roleLabel(event.toRole()) + ". Sign in again to continue.";
        notificationService.createNotification(user, NotificationEventType.user_role_changed, msg, "/dashboard");
    }

    private static String roleLabel(UserRole role) {
        if (role == null) {
            return "unknown";
        }
        String name = role.name();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    /**
     * Moderators are network-wide (no owning institution), so every moderator
     * is notified regardless of which institution an event originated from.
     */
    private List<User> allModerators() {
        return userRepository.findByRole(UserRole.moderator);
    }

    private List<User> institutionContributors(java.util.UUID institutionId) {
        if (institutionId == null) {
            return List.of();
        }
        return userRepository.findByInstitutionIdAndRoleOrderByCreatedAtDesc(institutionId, UserRole.contributor);
    }

    private List<User> superAdmins() {
        return userRepository.findByRole(UserRole.admin);
    }

    private static String fmt(Instant instant) {
        if (instant == null) {
            return "TBD";
        }
        return ZonedDateTime.ofInstant(instant, ZoneOffset.UTC).format(SLOT_FMT);
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }
}
