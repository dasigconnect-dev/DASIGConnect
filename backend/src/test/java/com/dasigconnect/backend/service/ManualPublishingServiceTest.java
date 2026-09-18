package com.dasigconnect.backend.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.event.PostPublishedManualEvent;
import com.dasigconnect.backend.event.SubmissionFastTrackRetryEvent;
import com.dasigconnect.backend.event.SubmissionRescheduledEvent;
import com.dasigconnect.backend.exception.GuardRailViolationException;
import com.dasigconnect.backend.exception.SubmissionNotFoundException;
import com.dasigconnect.backend.model.dto.guardrail.GuardRailResult;
import com.dasigconnect.backend.model.dto.resolution.ManualPublishCompleteDto;
import com.dasigconnect.backend.model.dto.submission.RescheduleRequestDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.security.JwtUserDetails;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ManualPublishingServiceTest {

    @Mock private SubmissionRepository submissionRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private GuardRailService guardRailService;
    @Mock private SlotReservationService slotReservationService;
    @Mock private EntityManager entityManager;

    @InjectMocks
    private ManualPublishingService service;

    private UUID submissionId;
    private UUID adminId;
    private JwtUserDetails admin;
    private JwtUserDetails moderator;

    @BeforeEach
    void setUp() {
        submissionId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        admin = new JwtUserDetails(adminId, "admin@dasig.gov.ph", "admin", null);
        moderator = new JwtUserDetails(UUID.randomUUID(), "moderator@dasig.gov.ph", "moderator", null);

        // @PersistenceContext is not injected by @InjectMocks — inject manually
        ReflectionTestUtils.setField(service, "entityManager", entityManager);

        // Lenient stub reused by tests that reach entityManager.getReference()
        when(entityManager.getReference(eq(User.class), eq(adminId))).thenReturn(user(adminId));
    }

    // ── start() ─────────────────────────────────────────────────────────────────

    @Test
    void start_publishFailed_setsTimestampAndAudits() {
        Submission s = submission(submissionId, SubmissionStatus.publish_failed);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));
        when(submissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.start(submissionId, admin);

        assertThat(s.getManualPublishStartedAt()).isNotNull();
        verify(auditLogService).record(any(), eq("MANUAL_PUBLISH_STARTED"), any(), any(), eq(submissionId), any());
    }

    @Test
    void start_scheduled_setsTimestampAndAudits() {
        Submission s = submission(submissionId, SubmissionStatus.scheduled);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));
        when(submissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.start(submissionId, admin);

        assertThat(s.getManualPublishStartedAt()).isNotNull();
        verify(auditLogService).record(any(), eq("MANUAL_PUBLISH_STARTED"), any(), any(), eq(submissionId), any());
    }

    @Test
    void start_ineligibleStatus_throwsConflict() {
        Submission s = submission(submissionId, SubmissionStatus.pending);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.start(submissionId, admin))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(submissionRepository, never()).save(any());
    }

    @Test
    void start_notFound_throwsNotFound() {
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(submissionId, admin))
                .isInstanceOf(SubmissionNotFoundException.class);
    }

    // ── complete() ───────────────────────────────────────────────────────────────

    @Test
    void complete_withValidUrl_transitionsToPublishedManual() {
        Submission s = submission(submissionId, SubmissionStatus.publish_failed);
        s.setManualPublishStartedAt(Instant.now().minusSeconds(60));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));
        when(submissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ManualPublishCompleteDto dto = new ManualPublishCompleteDto();
        dto.setPostUrl("https://www.facebook.com/dasig/posts/12345");
        dto.setNotes("Published successfully.");

        service.complete(submissionId, dto, admin);

        assertThat(s.getStatus()).isEqualTo(SubmissionStatus.published_manual);
        assertThat(s.getPublishedAt()).isNotNull();
        assertThat(s.getPublishedManualUrl()).isEqualTo("https://www.facebook.com/dasig/posts/12345");
        assertThat(s.getPublishedManualNotes()).isEqualTo("Published successfully.");
        assertThat(s.getManualPublishStartedAt()).isNull();
        verify(auditLogService).record(any(), eq("MANUAL_PUBLISH_COMPLETE"), any(), any(), eq(submissionId), any());
        verify(eventPublisher).publishEvent(any(PostPublishedManualEvent.class));
        // A locked SlotReservation serves no further purpose once the post is
        // actually out (see V95 migration / GR-H1 network-wide race fix, 2026-09-17).
        verify(slotReservationService).release(submissionId);
    }

    @Test
    void complete_withoutUrl_succeeds() {
        Submission s = submission(submissionId, SubmissionStatus.publish_failed);
        s.setManualPublishStartedAt(Instant.now().minusSeconds(10));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));
        when(submissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.complete(submissionId, new ManualPublishCompleteDto(), admin);

        assertThat(s.getStatus()).isEqualTo(SubmissionStatus.published_manual);
    }

    @Test
    void complete_sessionNotStarted_throwsConflict() {
        Submission s = submission(submissionId, SubmissionStatus.publish_failed);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.complete(submissionId, new ManualPublishCompleteDto(), admin))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void complete_invalidFacebookUrl_throwsBadRequest() {
        Submission s = submission(submissionId, SubmissionStatus.publish_failed);
        s.setManualPublishStartedAt(Instant.now().minusSeconds(10));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));

        ManualPublishCompleteDto dto = new ManualPublishCompleteDto();
        dto.setPostUrl("https://twitter.com/post/123");

        assertThatThrownBy(() -> service.complete(submissionId, dto, admin))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ── cancel() ─────────────────────────────────────────────────────────────────

    @Test
    void cancel_publishFailed_clearsTimestampAndAudits() {
        Submission s = submission(submissionId, SubmissionStatus.publish_failed);
        s.setManualPublishStartedAt(Instant.now().minusSeconds(10));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));
        when(submissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.cancel(submissionId, admin);

        assertThat(s.getManualPublishStartedAt()).isNull();
        verify(auditLogService).record(any(), eq("MANUAL_PUBLISH_CANCELLED"), any(), any(), eq(submissionId), any());
    }

    @Test
    void cancel_scheduled_clearsTimestampAndAudits() {
        Submission s = submission(submissionId, SubmissionStatus.scheduled);
        s.setManualPublishStartedAt(Instant.now().minusSeconds(30));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));
        when(submissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.cancel(submissionId, admin);

        assertThat(s.getManualPublishStartedAt()).isNull();
        verify(auditLogService).record(any(), eq("MANUAL_PUBLISH_CANCELLED"), any(), any(), eq(submissionId), any());
    }

    // ── retry() ──────────────────────────────────────────────────────────────────

    @Test
    void retry_publishFailed_resetsToScheduled() {
        Submission s = submission(submissionId, SubmissionStatus.publish_failed);
        s.setRetryCount(3);
        s.setManualPublishStartedAt(Instant.now().minusSeconds(60));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));
        when(submissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.retry(submissionId, admin);

        assertThat(s.getStatus()).isEqualTo(SubmissionStatus.scheduled);
        assertThat(s.getRetryCount()).isZero();
        assertThat(s.getManualPublishStartedAt()).isNull();
    }

    @Test
    void retry_nonPublishFailed_throwsConflict() {
        Submission s = submission(submissionId, SubmissionStatus.scheduled);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.retry(submissionId, admin))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void retry_fastTrack_firesImmediatePublishEvent_becauseTheCronNeverPicksItUp() {
        Submission s = submission(submissionId, SubmissionStatus.publish_failed);
        s.setFastTrack(true);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));
        when(submissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.retry(submissionId, moderator);

        assertThat(s.getStatus()).isEqualTo(SubmissionStatus.scheduled);
        assertThat(s.isFastTrack()).isTrue();
        verify(eventPublisher).publishEvent(any(SubmissionFastTrackRetryEvent.class));
    }

    @Test
    void retry_notFastTrack_doesNotFireImmediatePublishEvent() {
        Submission s = submission(submissionId, SubmissionStatus.publish_failed);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));
        when(submissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.retry(submissionId, moderator);

        verify(eventPublisher, never()).publishEvent(any(SubmissionFastTrackRetryEvent.class));
    }

    // ── retryWithNewSchedule() ─────────────────────────────────────────────────────

    @Test
    void retryWithNewSchedule_cleanGuardRails_reschedulesAndConfirms() {
        Submission s = submission(submissionId, SubmissionStatus.publish_failed);
        s.setRetryCount(2);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));
        when(submissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(guardRailService.validate(any(), any(), any())).thenReturn(new GuardRailResult());

        RescheduleRequestDto dto = new RescheduleRequestDto();
        Instant newSlot = Instant.now().plusSeconds(7200);
        dto.setScheduledAt(newSlot);

        service.retryWithNewSchedule(submissionId, dto, admin);

        assertThat(s.getStatus()).isEqualTo(SubmissionStatus.scheduled);
        assertThat(s.getScheduledAt()).isEqualTo(newSlot);
        assertThat(s.getRetryCount()).isZero();
        verify(slotReservationService).reserveLockedSlot(submissionId, s.getInstitution().getId(), newSlot, false);
        verify(eventPublisher).publishEvent(any(SubmissionRescheduledEvent.class));
    }

    @Test
    void retryWithNewSchedule_resetsModeratorRescheduleCapBaseline() {
        // Regression: a submission that had already used up its Moderator
        // reschedule cap (UC-3.1) before failing to publish must not come back
        // from a successful retry still capped out, or anchored to a now-stale
        // original slot far from the new one.
        Submission s = submission(submissionId, SubmissionStatus.publish_failed);
        Instant staleOriginal = Instant.now().minusSeconds(30 * 24 * 3600);
        s.setOriginalScheduledAt(staleOriginal);
        s.setModeratorRescheduleCount(2);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));
        when(submissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(guardRailService.validate(any(), any(), any())).thenReturn(new GuardRailResult());

        RescheduleRequestDto dto = new RescheduleRequestDto();
        Instant newSlot = Instant.now().plusSeconds(7200);
        dto.setScheduledAt(newSlot);

        service.retryWithNewSchedule(submissionId, dto, admin);

        assertThat(s.getOriginalScheduledAt()).isEqualTo(newSlot);
        assertThat(s.getModeratorRescheduleCount()).isZero();
    }

    @Test
    void retryWithNewSchedule_nonPublishFailed_throwsConflict() {
        Submission s = submission(submissionId, SubmissionStatus.scheduled);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));

        RescheduleRequestDto dto = new RescheduleRequestDto();
        dto.setScheduledAt(Instant.now().plusSeconds(7200));

        assertThatThrownBy(() -> service.retryWithNewSchedule(submissionId, dto, admin))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void retryWithNewSchedule_blockedWithoutOverride_throwsGuardRailViolation() {
        Submission s = submission(submissionId, SubmissionStatus.publish_failed);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));
        when(guardRailService.validate(any(), any(), any())).thenReturn(new GuardRailResult(
                java.util.List.of(new com.dasigconnect.backend.model.dto.guardrail.GuardRailViolation("GR-H2", "Too soon")),
                java.util.List.of()));

        RescheduleRequestDto dto = new RescheduleRequestDto();
        dto.setScheduledAt(Instant.now().plusSeconds(60));

        assertThatThrownBy(() -> service.retryWithNewSchedule(submissionId, dto, admin))
                .isInstanceOf(GuardRailViolationException.class);

        verify(submissionRepository, never()).save(any());
    }

    @Test
    void retryWithNewSchedule_missedReview_returnsToPendingApproval() {
        Submission s = submission(submissionId, SubmissionStatus.missed_review);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));
        when(submissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(guardRailService.validate(any(), any(), any())).thenReturn(new GuardRailResult());

        RescheduleRequestDto dto = new RescheduleRequestDto();
        Instant newSlot = Instant.now().plusSeconds(7200);
        dto.setScheduledAt(newSlot);

        service.retryWithNewSchedule(submissionId, dto, admin);

        assertThat(s.getStatus()).isEqualTo(SubmissionStatus.pending);
        assertThat(s.getScheduledAt()).isEqualTo(newSlot);
        assertThat(s.getSubmittedAt()).isNotNull();
        verify(slotReservationService).reserve(submissionId, s.getInstitution().getId(), newSlot);
        verify(auditLogService).record(any(), eq("MISSED_REVIEW_RETRY_NEW_SCHEDULE"), any(), any(), eq(submissionId), any());
    }

    @Test
    void retryWithNewSchedule_missedReview_blockedWithoutOverride_throwsGuardRailViolation() {
        Submission s = submission(submissionId, SubmissionStatus.missed_review);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));
        when(guardRailService.validate(any(), any(), any())).thenReturn(new GuardRailResult(
                java.util.List.of(new com.dasigconnect.backend.model.dto.guardrail.GuardRailViolation("GR-H2", "Too soon")),
                java.util.List.of()));

        RescheduleRequestDto dto = new RescheduleRequestDto();
        dto.setScheduledAt(Instant.now().plusSeconds(60));

        assertThatThrownBy(() -> service.retryWithNewSchedule(submissionId, dto, admin))
                .isInstanceOf(GuardRailViolationException.class);

        verify(submissionRepository, never()).save(any());
    }

    @Test
    void retryWithNewSchedule_fastTrackAsAdmin_clearsFastTrackAndSucceeds() {
        Submission s = submission(submissionId, SubmissionStatus.publish_failed);
        s.setFastTrack(true);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));
        when(submissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(guardRailService.validate(any(), any(), any())).thenReturn(new GuardRailResult());

        RescheduleRequestDto dto = new RescheduleRequestDto();
        Instant newSlot = Instant.now().plusSeconds(7200);
        dto.setScheduledAt(newSlot);

        service.retryWithNewSchedule(submissionId, dto, admin);

        assertThat(s.isFastTrack()).isFalse();
        assertThat(s.getScheduledAt()).isEqualTo(newSlot);
    }

    @Test
    void retryWithNewSchedule_fastTrackAsModerator_throwsForbidden() {
        Submission s = submission(submissionId, SubmissionStatus.publish_failed);
        s.setFastTrack(true);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));

        RescheduleRequestDto dto = new RescheduleRequestDto();
        dto.setScheduledAt(Instant.now().plusSeconds(7200));

        assertThatThrownBy(() -> service.retryWithNewSchedule(submissionId, dto, moderator))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void retryWithNewSchedule_notFastTrackAsModerator_isAllowed() {
        // No mode change happening here, so a Moderator can still freely reschedule.
        Submission s = submission(submissionId, SubmissionStatus.publish_failed);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));
        when(submissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(guardRailService.validate(any(), any(), any())).thenReturn(new GuardRailResult());

        RescheduleRequestDto dto = new RescheduleRequestDto();
        Instant newSlot = Instant.now().plusSeconds(7200);
        dto.setScheduledAt(newSlot);

        service.retryWithNewSchedule(submissionId, dto, moderator);

        assertThat(s.getScheduledAt()).isEqualTo(newSlot);
    }

    // ── retryAsLiveOverride() ────────────────────────────────────────────────────

    @Test
    void retryAsLiveOverride_asAdmin_convertsToLiveAndFiresEvent() {
        Submission s = submission(submissionId, SubmissionStatus.publish_failed);
        s.setScheduledAt(Instant.now().plusSeconds(3600));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));
        when(submissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.retryAsLiveOverride(submissionId, admin);

        assertThat(s.isFastTrack()).isTrue();
        assertThat(s.getScheduledAt()).isNull();
        assertThat(s.getStatus()).isEqualTo(SubmissionStatus.scheduled);
        verify(slotReservationService).release(submissionId);
        verify(auditLogService).record(any(), eq("PUBLISH_FAILED_RETRY_MODE_OVERRIDE_TO_LIVE"), any(), any(), eq(submissionId), any());
        verify(eventPublisher).publishEvent(any(SubmissionFastTrackRetryEvent.class));
    }

    @Test
    void retryAsLiveOverride_asModerator_throwsForbidden() {
        assertThatThrownBy(() -> service.retryAsLiveOverride(submissionId, moderator))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void retryAsLiveOverride_alreadyFastTrack_throwsConflict() {
        Submission s = submission(submissionId, SubmissionStatus.publish_failed);
        s.setFastTrack(true);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.retryAsLiveOverride(submissionId, admin))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void retryAsLiveOverride_nonPublishFailed_throwsConflict() {
        Submission s = submission(submissionId, SubmissionStatus.scheduled);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.retryAsLiveOverride(submissionId, admin))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    // ── clearAbandoned() ─────────────────────────────────────────────────────────

    @Test
    void clearAbandoned_clearsTimestampAndAuditsSystemAction() {
        Submission s = submission(submissionId, SubmissionStatus.publish_failed);
        s.setManualPublishStartedAt(Instant.now().minusSeconds(7200));
        when(submissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.clearAbandoned(s);

        assertThat(s.getManualPublishStartedAt()).isNull();
        verify(auditLogService).recordSystemAction(eq("MANUAL_PUBLISH_ABANDONED"), eq(submissionId), any());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private static Submission submission(UUID id, SubmissionStatus status) {
        Institution institution = new Institution();
        institution.setId(UUID.randomUUID());
        institution.setName("CIT-U");
        institution.setCode("CIT-U");
        institution.setEmailDomain("cit.edu.ph");

        Submission s = new Submission();
        s.setId(id);
        s.setStatus(status);
        s.setEventTitle("Tech Summit 2026");
        s.setContributor(user(UUID.randomUUID()));
        s.setInstitution(institution);
        return s;
    }

    private static User user(UUID id) {
        User u = new User();
        u.setId(id);
        u.setEmail("admin@dasig.gov.ph");
        u.setRole(UserRole.admin);
        return u;
    }
}
