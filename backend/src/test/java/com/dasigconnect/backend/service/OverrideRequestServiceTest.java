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

    private UUID contributorId;
    private UUID submissionId;
    private Instant slot;
    private Submission submission;
    private JwtUserDetails caller;

    @BeforeEach
    void setUp() {
        contributorId = UUID.randomUUID();
        submissionId = UUID.randomUUID();
        slot = Instant.now().plus(2, ChronoUnit.DAYS);

        User contributor = new User();
        contributor.setId(contributorId);
        Institution institution = new Institution();
        institution.setId(UUID.randomUUID());

        submission = new Submission();
        submission.setId(submissionId);
        submission.setEventTitle("Launch");
        submission.setStatus(SubmissionStatus.draft);
        submission.setContributor(contributor);
        submission.setInstitution(institution);

        caller = new JwtUserDetails(contributorId, "c@x.edu.ph", "contributor", institution.getId());

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
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

        service.create(dto(), caller);

        verify(overrideRequestRepository).save(any(OverrideRequest.class));
        verify(eventPublisher).publishEvent(any(OverrideRequestedEvent.class));
    }

    @Test
    void create_notOwner_throwsForbidden() {
        slotIsBlocked();
        JwtUserDetails stranger = new JwtUserDetails(UUID.randomUUID(), "s@x.edu.ph", "contributor", null);

        assertThatThrownBy(() -> service.create(dto(), stranger))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("do not own");
        verify(overrideRequestRepository, never()).save(any());
    }

    @Test
    void create_slotNotBlocked_throws422() {
        when(guardRailService.validate(any(), eq(slot))).thenReturn(new GuardRailResult());

        assertThatThrownBy(() -> service.create(dto(), caller))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not blocked");
        verify(overrideRequestRepository, never()).save(any());
    }

    @Test
    void create_existingPendingRequest_throwsConflict() {
        slotIsBlocked();
        when(overrideRequestRepository.findBySubmissionIdAndDecision(submissionId, OverrideRequestDecision.pending))
                .thenReturn(List.of(new OverrideRequest()));

        assertThatThrownBy(() -> service.create(dto(), caller))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already have a pending");
    }

    @Test
    void create_reasonTooShort_throws400() {
        slotIsBlocked();
        OverrideRequestCreateDto d = dto();
        d.setReason("too short");

        assertThatThrownBy(() -> service.create(d, caller))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("at least 10");
    }

    @Test
    void create_submissionAlreadyScheduled_throwsConflict() {
        slotIsBlocked();
        submission.setStatus(SubmissionStatus.scheduled);

        assertThatThrownBy(() -> service.create(dto(), caller))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("still being edited");
    }
}
