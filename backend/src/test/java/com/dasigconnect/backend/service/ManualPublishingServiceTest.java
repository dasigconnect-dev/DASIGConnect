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
import com.dasigconnect.backend.exception.SubmissionNotFoundException;
import com.dasigconnect.backend.model.dto.resolution.ManualPublishCompleteDto;
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
    @Mock private EntityManager entityManager;

    @InjectMocks
    private ManualPublishingService service;

    private UUID submissionId;
    private UUID adminId;
    private JwtUserDetails admin;

    @BeforeEach
    void setUp() {
        submissionId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        admin = new JwtUserDetails(adminId, "admin@dasig.gov.ph", "administrator", null);

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
        u.setRole(UserRole.administrator);
        return u;
    }
}
