package com.dasigconnect.backend.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.dasigconnect.backend.exception.GuardRailViolationException;
import com.dasigconnect.backend.exception.SlotAlreadyTakenException;
import com.dasigconnect.backend.model.dto.guardrail.GuardRailResult;
import com.dasigconnect.backend.model.dto.guardrail.GuardRailViolation;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.SlotReservation;
import com.dasigconnect.backend.model.entity.SlotReservationStatus;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.SlotReservationRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;

@ExtendWith(MockitoExtension.class)
class SlotReservationServiceTest {

    @Mock
    private SlotReservationRepository slotReservationRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private InstitutionRepository institutionRepository;

    @Mock
    private GuardRailService guardRailService;

    @InjectMocks
    private SlotReservationService slotReservationService;

    private UUID submissionId;
    private UUID institutionId;
    private Instant validSlot;
    private Submission mockSubmission;
    private Institution mockInstitution;

    @BeforeEach
    void setUp() {
        submissionId = UUID.randomUUID();
        institutionId = UUID.randomUUID();
        validSlot = Instant.now().plus(3, ChronoUnit.HOURS);

        mockSubmission = new Submission();
        mockSubmission.setId(submissionId);

        mockInstitution = new Institution();
        mockInstitution.setId(institutionId);

        lenient().when(guardRailService.validate(any(), any())).thenReturn(new GuardRailResult());
        ReflectionTestUtils.setField(slotReservationService, "guardRailsEnforced", true);
    }

    // ── reserve() ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("reserve()")
    class ReserveTests {

        @BeforeEach
        void setupReserveDefaults() {
            lenient().when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(mockSubmission));
            lenient().when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(mockInstitution));
            lenient().when(slotReservationRepository.releaseBySubmissionId(any())).thenReturn(0);
        }

        @Test
        @DisplayName("should save reservation with HELD status when guard rails pass")
        void shouldSaveHeldReservation_whenGuardRailsPass() {
            SlotReservation saved = new SlotReservation();
            saved.setStatus(SlotReservationStatus.held);
            when(slotReservationRepository.save(any())).thenReturn(saved);

            SlotReservation result = slotReservationService.reserve(submissionId, institutionId, validSlot);

            assertThat(result.getStatus()).isEqualTo(SlotReservationStatus.held);
            verify(slotReservationRepository).save(any(SlotReservation.class));
        }

        @Test
        @DisplayName("should release existing reservation before creating new one")
        void shouldReleaseExistingSlot_beforeSaving() {
            when(slotReservationRepository.save(any())).thenReturn(new SlotReservation());

            slotReservationService.reserve(submissionId, institutionId, validSlot);

            verify(slotReservationRepository).releaseBySubmissionId(submissionId);
        }

        @Test
        @DisplayName("should return existing reservation when submission already owns requested slot")
        void shouldReturnExistingReservation_whenSameSlotAlreadyHeld() {
            SlotReservation existing = new SlotReservation();
            existing.setSubmission(mockSubmission);
            existing.setInstitution(mockInstitution);
            existing.setScheduledAt(validSlot);
            existing.setStatus(SlotReservationStatus.held);
            when(slotReservationRepository.findActiveBySubmissionId(submissionId))
                    .thenReturn(Optional.of(existing));

            SlotReservation result = slotReservationService.reserve(submissionId, institutionId, validSlot);

            assertThat(result).isSameAs(existing);
            verify(guardRailService, never()).validate(any(), any());
            verify(slotReservationRepository, never()).releaseBySubmissionId(any());
            verify(slotReservationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw GuardRailViolationException when hard blocks present")
        void shouldThrow_whenHardBlockPresent() {
            GuardRailViolation block = new GuardRailViolation("GR-H2", "Too soon");
            GuardRailResult blocked = new GuardRailResult(List.of(block), List.of());
            when(guardRailService.validate(any(), any())).thenReturn(blocked);

            assertThatThrownBy(() -> slotReservationService.reserve(submissionId, institutionId, validSlot))
                    .isInstanceOf(GuardRailViolationException.class);

            verify(slotReservationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should save reservation when guard rail enforcement is disabled")
        void shouldSave_whenGuardRailEnforcementDisabled() {
            ReflectionTestUtils.setField(slotReservationService, "guardRailsEnforced", false);
            when(slotReservationRepository.save(any())).thenReturn(new SlotReservation());

            slotReservationService.reserve(submissionId, institutionId, validSlot);

            verify(guardRailService, never()).validate(any(), any());
            verify(slotReservationRepository).save(any(SlotReservation.class));
        }

        @Test
        @DisplayName("should throw SlotAlreadyTakenException on DB unique constraint violation")
        void shouldThrow_whenRaceConditionOccurs() {
            when(slotReservationRepository.save(any()))
                    .thenThrow(new DataIntegrityViolationException("unique constraint"));

            assertThatThrownBy(() -> slotReservationService.reserve(submissionId, institutionId, validSlot))
                    .isInstanceOf(SlotAlreadyTakenException.class);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when submission not found")
        void shouldThrow_whenSubmissionNotFound() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> slotReservationService.reserve(submissionId, institutionId, validSlot))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Submission not found");
        }

        @Test
        @DisplayName("should still save when only soft warnings present (non-blocking)")
        void shouldSave_whenOnlySoftWarningsPresent() {
            GuardRailViolation warning = new GuardRailViolation("GR-S1", "3 unpublished posts");
            GuardRailResult withWarning = new GuardRailResult(List.of(), List.of(warning));
            when(guardRailService.validate(any(), any())).thenReturn(withWarning);
            when(slotReservationRepository.save(any())).thenReturn(new SlotReservation());

            slotReservationService.reserve(submissionId, institutionId, validSlot);

            verify(slotReservationRepository).save(any(SlotReservation.class));
        }
    }

    // ── release() ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("release()")
    class ReleaseTests {

        @Test
        @DisplayName("should call releaseBySubmissionId and return updated count")
        void shouldReleaseSlot() {
            when(slotReservationRepository.releaseBySubmissionId(submissionId)).thenReturn(1);

            int result = slotReservationService.release(submissionId);

            assertThat(result).isEqualTo(1);
            verify(slotReservationRepository).releaseBySubmissionId(submissionId);
        }

        @Test
        @DisplayName("should return 0 when no active reservation exists")
        void shouldReturn0_whenNoActiveReservation() {
            when(slotReservationRepository.releaseBySubmissionId(submissionId)).thenReturn(0);

            int result = slotReservationService.release(submissionId);

            assertThat(result).isEqualTo(0);
        }
    }

    // ── confirm() ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("confirm()")
    class ConfirmTests {

        @Test
        @DisplayName("should transition reservation from HELD to LOCKED")
        void shouldLockReservation() {
            SlotReservation held = new SlotReservation();
            held.setStatus(SlotReservationStatus.held);
            when(slotReservationRepository.findActiveBySubmissionId(submissionId))
                    .thenReturn(Optional.of(held));
            when(slotReservationRepository.save(any())).thenReturn(held);

            slotReservationService.confirm(submissionId);

            assertThat(held.getStatus()).isEqualTo(SlotReservationStatus.locked);
            verify(slotReservationRepository).save(held);
        }

        @Test
        @DisplayName("should throw IllegalStateException when no active reservation found")
        void shouldThrow_whenNoReservationFound() {
            when(slotReservationRepository.findActiveBySubmissionId(submissionId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> slotReservationService.confirm(submissionId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No active reservation");
        }
    }

    // ── releaseStaleHeldReservations() ────────────────────────────────────────
    @Nested
    @DisplayName("releaseStaleHeldReservations()")
    class StaleReleaseTests {

        @Test
        @DisplayName("should release all stale held reservations and return their submission IDs")
        void shouldReleaseStaleReservations() {
            Submission sub1 = new Submission();
            sub1.setId(UUID.randomUUID());
            Submission sub2 = new Submission();
            sub2.setId(UUID.randomUUID());

            SlotReservation stale1 = new SlotReservation();
            stale1.setSubmission(sub1);
            stale1.setStatus(SlotReservationStatus.held);

            SlotReservation stale2 = new SlotReservation();
            stale2.setSubmission(sub2);
            stale2.setStatus(SlotReservationStatus.held);

            when(slotReservationRepository.findStaleHeldReservations(any()))
                    .thenReturn(List.of(stale1, stale2));
            when(slotReservationRepository.saveAll(any())).thenReturn(List.of(stale1, stale2));

            List<UUID> released = slotReservationService.releaseStaleHeldReservations();

            assertThat(released).hasSize(2);
            assertThat(stale1.getStatus()).isEqualTo(SlotReservationStatus.released);
            assertThat(stale2.getStatus()).isEqualTo(SlotReservationStatus.released);
        }

        @Test
        @DisplayName("should return empty list when no stale reservations exist")
        void shouldReturnEmpty_whenNoStaleReservations() {
            when(slotReservationRepository.findStaleHeldReservations(any())).thenReturn(List.of());

            List<UUID> released = slotReservationService.releaseStaleHeldReservations();

            assertThat(released).isEmpty();

        }
    }
}
