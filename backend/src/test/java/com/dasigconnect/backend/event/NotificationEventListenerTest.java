package com.dasigconnect.backend.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.NotificationEventType;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.service.EmailDeliveryService;
import com.dasigconnect.backend.service.MessengerDeliveryService;
import com.dasigconnect.backend.service.NotificationService;

class NotificationEventListenerTest {

    private NotificationService notificationService;
    private UserRepository userRepository;
    private EmailDeliveryService emailDeliveryService;
    private MessengerDeliveryService messengerDeliveryService;
    private NotificationEventListener listener;

    private Institution institution;
    private User admin;
    private User contributor;
    private User superAdmin;

    @BeforeEach
    void setUp() {
        notificationService = Mockito.mock(NotificationService.class);
        userRepository = Mockito.mock(UserRepository.class);
        emailDeliveryService = Mockito.mock(EmailDeliveryService.class);
        messengerDeliveryService = Mockito.mock(MessengerDeliveryService.class);

        listener = new NotificationEventListener(
                notificationService,
                userRepository,
                emailDeliveryService,
                messengerDeliveryService,
                "http://localhost:5173");

        institution = new Institution();
        institution.setId(UUID.randomUUID());
        institution.setName("CIT University");

        admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setEmail("admin@cit.edu");
        admin.setRole(UserRole.moderator);
        admin.setInstitution(institution);

        contributor = new User();
        contributor.setId(UUID.randomUUID());
        contributor.setEmail("contributor@cit.edu");
        contributor.setRole(UserRole.contributor);
        contributor.setInstitution(institution);

        superAdmin = new User();
        superAdmin.setId(UUID.randomUUID());
        superAdmin.setEmail("superadmin@dost.gov.ph");
        superAdmin.setRole(UserRole.admin);

        // Moderators are network-wide now — notified via findByRole, not institution scoping.
        when(userRepository.findByRole(UserRole.moderator))
                .thenReturn(List.of(admin));
        when(userRepository.findByInstitutionIdAndRoleOrderByCreatedAtDesc(institution.getId(), UserRole.contributor))
                .thenReturn(List.of(contributor));
        when(userRepository.findByRole(UserRole.admin))
                .thenReturn(List.of(superAdmin));
    }

    @Test
    void onSubmissionPending_T01_dispatchesInAppAndMessengerToAdmin() {
        Submission s = new Submission();
        s.setId(UUID.randomUUID());
        s.setEventTitle("AI Expo");
        s.setInstitution(institution);
        s.setContributor(contributor);

        listener.onSubmissionPending(new SubmissionPendingEvent(s));

        verify(notificationService).createNotification(
                eq(admin), eq(NotificationEventType.submission_pending), contains("AI Expo"), contains(s.getId().toString()));
        verify(messengerDeliveryService).sendToUser(eq(admin.getId()), contains("AI Expo"));
    }

    @Test
    void onSubmissionApproved_T02_dispatchesInAppAndEmailToContributor() {
        Submission s = new Submission();
        s.setId(UUID.randomUUID());
        s.setEventTitle("Tech Summit");
        s.setInstitution(institution);
        s.setContributor(contributor);
        s.setScheduledAt(Instant.now());

        listener.onSubmissionApproved(new SubmissionApprovedEvent(s));

        verify(notificationService).createNotification(
                eq(contributor), eq(NotificationEventType.submission_approved), contains("Tech Summit"), any());
        verify(emailDeliveryService).send(
                eq(contributor), eq(NotificationEventType.submission_approved.name()), any(), any());
    }

    @Test
    void onSubmissionEditedDuringReview_flagged_dispatchesInAppAndEmailToContributor() {
        Submission s = new Submission();
        s.setId(UUID.randomUUID());
        s.setEventTitle("Science Fair");
        s.setInstitution(institution);
        s.setContributor(contributor);

        listener.onSubmissionEditedDuringReview(new SubmissionEditedDuringReviewEvent(
                s, com.dasigconnect.backend.model.entity.ReviewEditSeverity.FLAGGED, null));

        verify(notificationService).createNotification(
                eq(contributor), eq(NotificationEventType.submission_edited_in_review),
                contains("Science Fair"), contains(s.getId().toString()));
        verify(emailDeliveryService).send(
                eq(contributor), eq(NotificationEventType.submission_edited_in_review.name()), any(), any());
    }

    @Test
    void onSubmissionEditedDuringReview_quiet_isInAppOnly() {
        Submission s = new Submission();
        s.setId(UUID.randomUUID());
        s.setEventTitle("Open House");
        s.setInstitution(institution);
        s.setContributor(contributor);

        listener.onSubmissionEditedDuringReview(new SubmissionEditedDuringReviewEvent(
                s, com.dasigconnect.backend.model.entity.ReviewEditSeverity.QUIET, null));

        verify(notificationService).createNotification(
                eq(contributor), eq(NotificationEventType.submission_edited_in_review), any(), any());
        verify(emailDeliveryService, Mockito.never()).send(
                any(), eq(NotificationEventType.submission_edited_in_review.name()), any(), any());
    }

    @Test
    void onSubmissionRejected_T03_dispatchesInAppAndEmailToContributor() {
        Submission s = new Submission();
        s.setId(UUID.randomUUID());
        s.setEventTitle("Poster Presentation");
        s.setInstitution(institution);
        s.setContributor(contributor);

        listener.onSubmissionRejected(new SubmissionRejectedEvent(s, "Image quality is too low"));

        verify(notificationService).createNotification(
                eq(contributor), eq(NotificationEventType.submission_rejected), contains("Poster Presentation"), any());
        verify(emailDeliveryService).send(
                eq(contributor), eq(NotificationEventType.submission_rejected.name()), any(), contains("Image quality"));
    }

    @Test
    void onPostPublished_T04_dispatchesInAppToContributor() {
        Submission s = new Submission();
        s.setId(UUID.randomUUID());
        s.setEventTitle("Robotics Meetup");
        s.setInstitution(institution);
        s.setContributor(contributor);

        listener.onPostPublished(new PostPublishedEvent(s, "https://facebook.com/post/123"));

        verify(notificationService).createNotification(
                eq(contributor), eq(NotificationEventType.submission_published), contains("Robotics Meetup"), eq("https://facebook.com/post/123"));
    }

    @Test
    void onPublishFailed_T06_dispatchesInAppEmailAndMessengerToAdminAndInAppToContributor() {
        Submission s = new Submission();
        s.setId(UUID.randomUUID());
        s.setEventTitle("Webinar");
        s.setInstitution(institution);
        s.setContributor(contributor);

        listener.onPublishFailed(new PublishFailedEvent(s, "Graph API Error 190"));

        verify(notificationService).createNotification(
                eq(admin), eq(NotificationEventType.submission_publish_failed), contains("Webinar"), any());
        verify(emailDeliveryService).send(
                eq(admin), eq(NotificationEventType.submission_publish_failed.name()), any(), contains("Graph API Error 190"));
        verify(messengerDeliveryService).sendToUser(eq(admin.getId()), contains("Webinar"));
        verify(notificationService).createNotification(
                eq(contributor), eq(NotificationEventType.submission_publish_failed), contains("Webinar"), any());
    }

    @Test
    void onEmptySchedule_T07_dispatchesInAppWithSuggestionsToAdminsAndContributors() {
        listener.onEmptySchedule(new EmptyScheduleEvent(institution, List.of("Cover upcoming hackathon", "Spotlight faculty awardees")));

        verify(notificationService).createNotification(
                eq(admin), eq(NotificationEventType.empty_schedule_warning), contains("Cover upcoming hackathon"), any());
        verify(notificationService).createNotification(
                eq(contributor), eq(NotificationEventType.empty_schedule_warning), contains("Cover upcoming hackathon"), any());
    }

    @Test
    void onFastTrackSubmission_T11_dispatchesInAppEmailAndMessengerToAdmin() {
        Submission s = new Submission();
        s.setId(UUID.randomUUID());
        s.setEventTitle("Breaking Science News");
        s.setInstitution(institution);
        s.setContributor(contributor);

        listener.onFastTrackSubmission(new FastTrackSubmissionEvent(s));

        verify(notificationService).createNotification(
                eq(admin), eq(NotificationEventType.fast_track_submission), contains("Breaking Science News"), any());
        verify(emailDeliveryService).send(
                eq(admin), eq(NotificationEventType.fast_track_submission.name()), any(), any());
        verify(messengerDeliveryService).sendToUser(eq(admin.getId()), contains("Breaking Science News"));
    }

    @Test
    void onEmbeddingFailureDigest_T12_dispatchesInAppToSuperAdmin() {
        listener.onEmbeddingFailureDigest(new EmbeddingFailureDigestEvent(3L, List.of("img1.jpg", "img2.jpg")));

        verify(notificationService).createNotification(
                eq(superAdmin), eq(NotificationEventType.embedding_failure_digest), contains("3 media asset(s)"), any());
    }
}
