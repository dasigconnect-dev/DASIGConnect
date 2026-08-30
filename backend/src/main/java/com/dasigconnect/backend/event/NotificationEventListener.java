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
        String scheduledPart = s.getScheduledAt() != null ? " (scheduled for " + fmt(s.getScheduledAt()) + ")" : "";
        String msg = who(s.getContributor()) + " submitted '" + s.getEventTitle() + "' for review" + scheduledPart + ".";
        String link = "/submissions/" + s.getId();

        for (User moderator : allModerators()) {
            notificationService.createNotification(moderator, NotificationEventType.submission_pending, msg, link);
            String messengerMsg = "New submission to review: \"" + s.getEventTitle()
                    + "\". Open DASIGConnect: " + frontendBaseUrl + link;
            messengerDeliveryService.sendToUser(moderator.getId(), messengerMsg);
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
        String msg = "'" + s.getEventTitle() + "' was approved and is scheduled for " + slot + ".";
        if (event.edited()) {
            msg += " A moderator edited it before scheduling — open it to see what changed.";
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
        String msg = "'" + s.getEventTitle() + "' was not approved. Open it to see the moderator's reason.";
        String link = "/submissions/" + s.getId();

        notificationService.createNotification(contributor, NotificationEventType.submission_rejected, msg, link);
        String emailBody = msg + "\n\nReason:\n" + event.reason()
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
        String msg = "'" + s.getEventTitle()
                + "' needs changes before it can be approved. Open it to see the moderator's notes.";
        String link = "/submissions/" + s.getId();

        notificationService.createNotification(contributor, NotificationEventType.submission_needs_revision, msg, link);
        String emailBody = msg + "\n\nModerator notes:\n" + event.remarks()
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
        String msg = "'" + s.getEventTitle() + "' is now live on the DASIG Facebook Page.";
        String link = event.platformPostUrl() != null ? event.platformPostUrl() : "/submissions/" + s.getId();
        notificationService.createNotification(s.getContributor(), NotificationEventType.submission_published, msg, link);
    }

    // ── T-05 — Post Published (Manual Fallback) ───────────────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPostPublishedManual(PostPublishedManualEvent event) {
        Submission s = event.submission();
        String postLink = event.platformPostUrl() != null ? event.platformPostUrl() : "/submissions/" + s.getId();

        String contributorMsg = "'" + s.getEventTitle()
                + "' was published to the DASIG Facebook Page manually by a moderator.";
        notificationService.createNotification(
                s.getContributor(), NotificationEventType.submission_published_manual, contributorMsg, postLink);

        String moderatorMsg = "'" + s.getEventTitle() + "' from "
                + s.getInstitution().getName() + " was published manually.";
        for (User moderator : allModerators()) {
            notificationService.createNotification(moderator, NotificationEventType.submission_published_manual, moderatorMsg, postLink);
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
                + "' (scheduled " + slot + "): " + event.errorDetail()
                + ". Recover it from the Review Queue's Failed tab.";

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
                    adminMsg + "\n\nRecover it here: " + frontendBaseUrl + "/validation/queue?tab=failed");
            // Messenger alert (A4 / A5)
            String messengerAlert = "Urgent: automated publishing failed for \"" + s.getEventTitle()
                    + "\". Manual action required: " + frontendBaseUrl + link;
            messengerDeliveryService.sendToUser(admin.getId(), messengerAlert);
        }

        String contributorMsg = "'" + s.getEventTitle()
                + "' could not be published automatically. A moderator has been notified and will follow up.";
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

        String contributorMsg = "'" + s.getEventTitle()
                + "' missed its scheduled slot before it was reviewed. A moderator will"
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
        sb.append(instName).append(" has no posts scheduled for the week ahead.");
        if (event.suggestions() != null && !event.suggestions().isEmpty()) {
            sb.append("\n\nIdeas:");
            for (String suggestion : event.suggestions()) {
                sb.append("\n• ").append(suggestion);
            }
        }
        String msg = sb.toString();
        String link = "/scheduler/calendar";

        // A week-long content gap for a member HEI is a review + network
        // planning concern — notify every moderator, every admin, and that
        // institution's own contributors. Roles are mutually exclusive, so the
        // three lists never overlap.
        for (User moderator : allModerators()) {
            notificationService.createNotification(moderator, NotificationEventType.empty_schedule_warning, msg, link);
        }
        for (User admin : superAdmins()) {
            notificationService.createNotification(admin, NotificationEventType.empty_schedule_warning, msg, link);
        }
        for (User contributor : institutionContributors(event.institution().getId())) {
            notificationService.createNotification(contributor, NotificationEventType.empty_schedule_warning, msg, link);
        }
    }

    // ── T-08 — Token Expiry Warning (GR-T3) ───────────────────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTokenExpiryWarning(TokenExpiryWarningEvent event) {
        String msg = "The Facebook page access token expires in " + days(event.daysUntilExpiry())
                + ". Re-authenticate it under System Health -> Integrations to avoid a publishing outage.";
        String link = "/admin/system-health#integrations";
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
        String msg = "The Facebook page access token failed validation. "
                + "Automated publishing is paused until you re-authenticate it under System Health -> Integrations.";
        String link = "/admin/system-health#integrations";
        for (User admin : superAdmins()) {
            notificationService.createNotification(admin, NotificationEventType.token_invalid, msg, link);
            emailDeliveryService.send(admin,
                    NotificationEventType.token_invalid.name(),
                    "DASIGConnect — CRITICAL: Token validation failed",
                    msg + "\n\nManage the token: " + frontendBaseUrl + link);
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
                "Publishing for '" + s.getEventTitle()
                + "' is on hold — the Facebook page access token has expired. "
                + "The post stays scheduled until the token is re-authenticated.";
            case ESCALATION_24H ->
                "'" + s.getEventTitle()
                + "' has been blocked by an expired Facebook page access token for 24 hours. "
                + "Re-authenticate the token to resume automated publishing.";
            case FINAL_FAILURE ->
                "'" + s.getEventTitle()
                + "' has been blocked by an expired Facebook page access token for 48 hours "
                + "and was moved to Publish Failed for manual recovery.";
        };
        if (event.detail() != null && !event.detail().isBlank()) {
            msg = msg + " Detail: " + event.detail();
        }
        for (User admin : superAdmins()) {
            notificationService.createNotification(admin, NotificationEventType.token_invalid, msg, link);
            emailDeliveryService.send(admin,
                    NotificationEventType.token_invalid.name(),
                    "DASIGConnect - Facebook token publishing alert",
                    msg + "\n\nView submission: " + frontendBaseUrl + link);
        }
    }

    // ── T-10 — Moderator rescheduled a post ────────────────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSubmissionRescheduled(SubmissionRescheduledEvent event) {
        Submission s = event.submission();
        String msg = "A moderator moved '" + s.getEventTitle()
                + "' from " + fmt(event.originalSlot()) + " to " + fmt(event.newSlot()) + ".";
        String link = "/submissions/" + s.getId();
        notificationService.createNotification(s.getContributor(), NotificationEventType.submission_rescheduled, msg, link);
    }

    // ── T-11 — Fast-Track Live Event Submission ───────────────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFastTrackSubmission(FastTrackSubmissionEvent event) {
        Submission s = event.submission();
        String msg = "Fast-track: " + who(s.getContributor()) + " submitted '"
                + s.getEventTitle() + "' for a live event and it needs immediate review.";
        String link = "/submissions/" + s.getId();

        for (User moderator : allModerators()) {
            notificationService.createNotification(moderator, NotificationEventType.fast_track_submission, msg, link);
            emailDeliveryService.send(moderator,
                    NotificationEventType.fast_track_submission.name(),
                    "Urgent: fast-track submission needs review",
                    msg + "\n\nOpen DASIGConnect: " + frontendBaseUrl + link);
            String messengerMsg = "Urgent fast-track submission: \"" + s.getEventTitle()
                    + "\". Open DASIGConnect: " + frontendBaseUrl + link;
            messengerDeliveryService.sendToUser(moderator.getId(), messengerMsg);
        }
    }

    // ── T-12 — Embedding Reconciliation Failure Digest ────────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEmbeddingFailureDigest(EmbeddingFailureDigestEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("Weekly AI digest: ").append(event.failedCount())
                .append(" media asset(s) failed to generate a search embedding.");
        if (event.sampleFilenames() != null && !event.sampleFilenames().isEmpty()) {
            sb.append(" Affected files: ").append(String.join(", ", event.sampleFilenames()));
        }
        String msg = sb.toString();
        String link = "/media-repository";

        for (User admin : superAdmins()) {
            notificationService.createNotification(admin, NotificationEventType.embedding_failure_digest, msg, link);
        }
    }

    // ── Guard rail override requests ────────────────────────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOverrideRequested(OverrideRequestedEvent event) {
        Submission s = event.submission();
        String link = "/submissions/" + s.getId();
        String msg = who(event.contributor()) + " requested a guard rail override for '"
                + s.getEventTitle() + "' — " + event.violatedRule() + " at " + fmt(event.requestedSlot())
                + ". Approve, suggest another slot, or deny it.";
        for (User admin : superAdmins()) {
            notificationService.createNotification(admin, NotificationEventType.override_requested, msg, link);
            emailDeliveryService.send(admin,
                    NotificationEventType.override_requested.name(),
                    "DASIGConnect — Guard rail override requested",
                    msg + (event.reason() != null ? "\n\nReason: " + event.reason() : "")
                        + "\n\nReview it: " + frontendBaseUrl + link);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOverrideApproved(OverrideApprovedEvent event) {
        Submission s = event.submission();
        String link = "/submissions/" + s.getId();

        String contributorMsg = "Your guard rail override for '" + s.getEventTitle()
                + "' was approved — you can keep your chosen slot.";
        notificationService.createNotification(event.contributor(), NotificationEventType.override_approved, contributorMsg, link);

        String moderatorMsg = "A guard rail override was approved for " + who(event.contributor())
                + " — '" + s.getEventTitle() + "'.";
        for (User moderator : allModerators()) {
            notificationService.createNotification(moderator, NotificationEventType.override_approved, moderatorMsg, link);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOverrideDenied(OverrideDeniedEvent event) {
        Submission s = event.submission();
        String link = "/submissions/" + s.getId();
        String msg = "Your guard rail override for '" + s.getEventTitle() + "' was not approved.";

        notificationService.createNotification(event.contributor(), NotificationEventType.override_denied, msg, link);
        String emailBody = msg + (event.reason() != null ? "\n\nReason: " + event.reason() : "")
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
        String msg = "An administrator suggested " + fmt(event.suggestedSlot())
                + " as an alternative slot for '" + s.getEventTitle()
                + "'. You can accept it, pick another compliant slot, or submit a new override request.";

        notificationService.createNotification(event.contributor(), NotificationEventType.override_slot_suggested, msg, link);
        emailDeliveryService.send(event.contributor(),
                NotificationEventType.override_slot_suggested.name(),
                "DASIGConnect — Alternative slot suggested",
                msg + "\n\nView submission: " + frontendBaseUrl + link);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInstitutionNoModerator(InstitutionNoModeratorEvent event) {
        String name = event.institution().getName();
        String msg = name + " has no active moderator. Its pending submissions are being escalated "
                + "until one is assigned.";
        String link = "/admin/institution-management";
        for (User admin : superAdmins()) {
            notificationService.createNotification(admin, NotificationEventType.institution_no_moderator, msg, link);
            emailDeliveryService.send(admin,
                    NotificationEventType.institution_no_moderator.name(),
                    "DASIGConnect — No active moderator at " + name,
                    msg + "\n\nManage institutions: " + frontendBaseUrl + link);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInstitutionOnboarded(InstitutionOnboardedEvent event) {
        String name = event.institution().getName();
        String msg = name + " finished onboarding — its moderator account is active and the workspace is ready.";
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
                + " to " + roleLabel(event.toRole()) + ". Please sign in again.";
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

    /** A human label for a contributor — full name if set, otherwise the email. */
    private static String who(User contributor) {
        if (contributor == null) {
            return "A contributor";
        }
        String first = contributor.getFirstName();
        String last = contributor.getLastName();
        if (first != null && !first.isBlank()) {
            return last != null && !last.isBlank() ? (first + " " + last).trim() : first.trim();
        }
        return contributor.getEmail() != null ? contributor.getEmail() : "A contributor";
    }

    private static String days(int n) {
        return n == 1 ? "1 day" : n + " days";
    }
}
