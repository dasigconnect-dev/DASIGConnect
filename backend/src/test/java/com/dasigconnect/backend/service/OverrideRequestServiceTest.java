package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.event.OverrideRequestedEvent;
import com.dasigconnect.backend.model.dto.exception.OverrideRequestCreateDto;
import com.dasigconnect.backend.model.dto.guardrail.GuardRailResult;
import com.dasigconnect.backend.model.dto.guardrail.GuardRailViolation;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.OverrideRequest;
import com.dasigconnect.backend.model.entity.OverrideRequestDecision;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.OverrideRequestRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OverrideRequestServiceTest {

    @Mock private OverrideRequestRepository overrideRequestRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private UserRepository userRepository;
    @Mock private SlotReservationService slotReservationService;
    @Mock private GuardRailService guardRailService;
    @Mock private AuditLogService auditLogService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private OverrideRequestService service;

    private UUID moderatorId;
    private UUID submissionId;
    private Instant slot;
    private Submission submission;
    private JwtUserDetails moderator;

    @BeforeEach
    void setUp() {
        moderatorId = UUID.randomUUID();
        submissionId = UUID.randomUUID();
        slot = Instant.now().plus(2, ChronoUnit.DAYS);

        User contributor = new User();
        contributor.setId(UUID.randomUUID());
        Institution institution = new Institution();
        institution.setId(UUID.randomUUID());

        submission = new Submission();
        submission.setId(submissionId);
        submission.setEventTitle("Launch");
        submission.setStatus(SubmissionStatus.in_review);
        submission.setContributor(contributor);
        submission.setInstitution(institution);

        moderator = new JwtUserDetails(moderatorId, "m@x.edu.ph", "moderator", null);

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(userRepository.getReferenceById(moderatorId)).thenReturn(new User());
        when(overrideRequestRepository.save(any(OverrideRequest.class)))
                .thenAnswer(inv -> {
                    OverrideRequest r = inv.getArgument(0);
                    if (r.getId() == null) r.setId(UUID.randomUUID());
                    return r;
                });
        when(overrideRequestRepository.findBySubmissionIdAndDecision(submissionId, OverrideRequestDecision.pending))
                .thenReturn(List.of());
    }

    private OverrideRequestCreateDto dto() {
        OverrideRequestCreateDto d = new OverrideRequestCreateDto();
        d.setSubmissionId(submissionId);
        d.setRequestedSlot(slot);
        d.setReason("Founding day is fixed and cannot move.");
        return d;
    }

    private void slotIsBlocked() {
        when(guardRailService.validate(any(), eq(slot)))
                .thenReturn(new GuardRailResult(List.of(new GuardRailViolation("GR-H1", "blocked")), List.of()));
    }

    @Test
    void create_blockedSlotWithReason_savesPendingAndPublishesEvent() {
        slotIsBlocked();

        service.create(dto(), moderator);

        verify(overrideRequestRepository).save(any(OverrideRequest.class));
        verify(eventPublisher).publishEvent(any(OverrideRequestedEvent.class));
    }

    @Test
    void create_scheduledSubmission_isAllowed() {
        submission.setStatus(SubmissionStatus.scheduled);
        slotIsBlocked();

        service.create(dto(), moderator);

        verify(overrideRequestRepository).save(any(OverrideRequest.class));
    }

    @Test
    void create_draftSubmission_throwsConflict() {
        submission.setStatus(SubmissionStatus.draft);
        slotIsBlocked();

        assertThatThrownBy(() -> service.create(dto(), moderator))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("in review or already scheduled");
        verify(overrideRequestRepository, never()).save(any());
    }

    @Test
    void create_slotNotBlocked_throws422() {
        when(guardRailService.validate(any(), eq(slot))).thenReturn(new GuardRailResult());

        assertThatThrownBy(() -> service.create(dto(), moderator))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not blocked");
        verify(overrideRequestRepository, never()).save(any());
    }

    @Test
    void create_existingPendingRequest_throwsConflict() {
        slotIsBlocked();
        when(overrideRequestRepository.findBySubmissionIdAndDecision(submissionId, OverrideRequestDecision.pending))
                .thenReturn(List.of(new OverrideRequest()));

        assertThatThrownBy(() -> service.create(dto(), moderator))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already a pending");
    }

    @Test
    void create_reasonTooShort_throws400() {
        slotIsBlocked();
        OverrideRequestCreateDto d = dto();
        d.setReason("too short");

        assertThatThrownBy(() -> service.create(d, moderator))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("at least 10");
    }
}
