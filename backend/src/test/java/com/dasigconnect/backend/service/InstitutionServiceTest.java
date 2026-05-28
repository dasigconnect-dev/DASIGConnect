package com.dasigconnect.backend.service;

import java.util.Map;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dasigconnect.backend.exception.InstitutionNotFoundException;
import com.dasigconnect.backend.model.dto.institution.CreateInstitutionRequest;
import com.dasigconnect.backend.model.dto.institution.InstitutionDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.InstitutionStatus;
import com.dasigconnect.backend.repository.InstitutionRepository;

@ExtendWith(MockitoExtension.class)
class InstitutionServiceTest {

    @Mock
    private InstitutionRepository institutionRepository;

    @Mock
    private WorkspaceProvisionerService workspaceProvisioner;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private InstitutionService institutionService;

    private Institution mockInstitution;
    private UUID institutionId;

    @BeforeEach
    void setUp() {
        institutionId = UUID.randomUUID();
        mockInstitution = new Institution();
        mockInstitution.setId(institutionId);
        mockInstitution.setName("Cebu Institute of Technology - University");
        mockInstitution.setCode("CIT-U");
        mockInstitution.setStatus(InstitutionStatus.inactive);
    }

    // ── createInstitution ─────────────────────────────────────────────────────
    @Nested
    @DisplayName("createInstitution()")
    class CreateInstitutionTests {

        @Test
        @DisplayName("should create institution with INACTIVE status")
        void shouldCreateInstitution_withInactiveStatus() {
            when(institutionRepository.existsByCode("CIT-U")).thenReturn(false);
            when(institutionRepository.save(any())).thenReturn(mockInstitution);

            CreateInstitutionRequest req = new CreateInstitutionRequest("Cebu Institute of Technology - University", "CIT-U", "cit.edu.ph");
            InstitutionDto result = institutionService.createInstitution(req);

            assertThat(result.getStatus()).isEqualTo(InstitutionStatus.inactive);
            assertThat(result.getInstitutionCode()).isEqualTo("CIT-U");
            assertThat(result.getName()).isEqualTo("Cebu Institute of Technology - University");
        }

        @Test
        @DisplayName("should call workspaceProvisioner after saving")
        void shouldProvisionWorkspace_afterSave() {
            when(institutionRepository.existsByCode(anyString())).thenReturn(false);
            when(institutionRepository.save(any())).thenReturn(mockInstitution);

            institutionService.createInstitution(new CreateInstitutionRequest("Test Uni", "TU", "tu.edu.ph"));

            verify(workspaceProvisioner, times(1)).provision(any(Institution.class));
        }

        @Test
        @DisplayName("should write audit log after creation")
        void shouldWriteAuditLog_afterCreation() {
            when(institutionRepository.existsByCode(anyString())).thenReturn(false);
            when(institutionRepository.save(any())).thenReturn(mockInstitution);

            institutionService.createInstitution(new CreateInstitutionRequest("Test Uni", "TU", "tu.edu.ph"));

            verify(auditLogService, times(1)).recordSystemAction(
                    eq("INSTITUTION_CREATED"), any(UUID.class), org.mockito.ArgumentMatchers.<Map<String, ?>>any());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when institution code already exists")
        void shouldThrow_whenCodeAlreadyExists() {
            when(institutionRepository.existsByCode("CIT-U")).thenReturn(true);

            CreateInstitutionRequest req = new CreateInstitutionRequest("Another CIT", "CIT-U", "another.edu.ph");

            assertThatThrownBy(() -> institutionService.createInstitution(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CIT-U");
        }

        @Test
        @DisplayName("should not call save when code already exists")
        void shouldNotSave_whenCodeAlreadyExists() {
            when(institutionRepository.existsByCode("CIT-U")).thenReturn(true);

            try {
                institutionService.createInstitution(new CreateInstitutionRequest("Test", "CIT-U", "test.edu.ph"));
            } catch (IllegalArgumentException ignored) {
            }

            verify(institutionRepository, never()).save(any());
        }
    }

    // ── getInstitution ────────────────────────────────────────────────────────
    @Nested
    @DisplayName("getInstitution()")
    class GetInstitutionTests {

        @Test
        @DisplayName("should return InstitutionDto when found")
        void shouldReturnDto_whenFound() {
            when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(mockInstitution));

            InstitutionDto result = institutionService.getInstitution(institutionId);

            assertThat(result.getId()).isEqualTo(institutionId);
            assertThat(result.getInstitutionCode()).isEqualTo("CIT-U");
        }

        @Test
        @DisplayName("should throw InstitutionNotFoundException when not found")
        void shouldThrow404_whenNotFound() {
            when(institutionRepository.findById(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> institutionService.getInstitution(UUID.randomUUID()))
                    .isInstanceOf(InstitutionNotFoundException.class);
        }
    }

    // ── transitionToPending ───────────────────────────────────────────────────
    @Nested
    @DisplayName("transitionToPending() — INACTIVE → PENDING")
    class TransitionToPendingTests {

        @Test
        @DisplayName("should transition from INACTIVE to PENDING")
        void shouldTransition_fromInactiveToPending() {
            when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(mockInstitution));
            when(institutionRepository.save(any())).thenReturn(mockInstitution);

            institutionService.transitionToPending(institutionId);

            assertThat(mockInstitution.getStatus()).isEqualTo(InstitutionStatus.pending);
            verify(institutionRepository).save(mockInstitution);
        }

        @Test
        @DisplayName("should write audit log on transition to PENDING")
        void shouldWriteAuditLog_onPending() {
            when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(mockInstitution));
            when(institutionRepository.save(any())).thenReturn(mockInstitution);

            institutionService.transitionToPending(institutionId);

            verify(auditLogService).recordSystemAction(
                    eq("INSTITUTION_PENDING"), eq(institutionId), org.mockito.ArgumentMatchers.<Map<String, ?>>any());
        }

        @Test
        @DisplayName("should reject transition when not in INACTIVE state")
        void shouldRejectTransition_whenNotInactive() {
            mockInstitution.setStatus(InstitutionStatus.active);
            when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(mockInstitution));

            assertThatThrownBy(() -> institutionService.transitionToPending(institutionId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("inactive");
        }
    }

    // ── transitionToActive ────────────────────────────────────────────────────
    @Nested
    @DisplayName("transitionToActive() — PENDING → ACTIVE")
    class TransitionToActiveTests {

        @BeforeEach
        void setPending() {
            mockInstitution.setStatus(InstitutionStatus.pending);
        }

        @Test
        @DisplayName("should transition from PENDING to ACTIVE")
        void shouldTransition_fromPendingToActive() {
            when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(mockInstitution));
            when(institutionRepository.save(any())).thenReturn(mockInstitution);

            institutionService.transitionToActive(institutionId);

            assertThat(mockInstitution.getStatus()).isEqualTo(InstitutionStatus.active);
            verify(institutionRepository).save(mockInstitution);
        }

        @Test
        @DisplayName("should also accept INACTIVE as precondition")
        void shouldTransition_fromInactiveToActive() {
            mockInstitution.setStatus(InstitutionStatus.inactive);
            when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(mockInstitution));
            when(institutionRepository.save(any())).thenReturn(mockInstitution);

            institutionService.transitionToActive(institutionId);

            assertThat(mockInstitution.getStatus()).isEqualTo(InstitutionStatus.active);
        }

        @Test
        @DisplayName("should write audit log on activation")
        void shouldWriteAuditLog_onActivation() {
            when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(mockInstitution));
            when(institutionRepository.save(any())).thenReturn(mockInstitution);

            institutionService.transitionToActive(institutionId);

            verify(auditLogService).recordSystemAction(
                    eq("INSTITUTION_ACTIVATED"), eq(institutionId), org.mockito.ArgumentMatchers.<Map<String, ?>>any());
        }

        @Test
        @DisplayName("should reject transition when already ACTIVE")
        void shouldRejectTransition_whenAlreadyActive() {
            mockInstitution.setStatus(InstitutionStatus.active);
            when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(mockInstitution));

            assertThatThrownBy(() -> institutionService.transitionToActive(institutionId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("pending");
        }
    }

    // ── transitionToInactive ──────────────────────────────────────────────────
    @Nested
    @DisplayName("transitionToInactive() — ACTIVE or PENDING → INACTIVE")
    class TransitionToInactiveTests {

        @Test
        @DisplayName("should transition from ACTIVE to INACTIVE")
        void shouldTransition_fromActiveToInactive() {
            mockInstitution.setStatus(InstitutionStatus.active);
            when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(mockInstitution));
            when(institutionRepository.save(any())).thenReturn(mockInstitution);

            institutionService.transitionToInactive(institutionId);

            assertThat(mockInstitution.getStatus()).isEqualTo(InstitutionStatus.inactive);
        }

        @Test
        @DisplayName("should transition from PENDING to INACTIVE")
        void shouldTransition_fromPendingToInactive() {
            mockInstitution.setStatus(InstitutionStatus.pending);
            when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(mockInstitution));
            when(institutionRepository.save(any())).thenReturn(mockInstitution);

            institutionService.transitionToInactive(institutionId);

            assertThat(mockInstitution.getStatus()).isEqualTo(InstitutionStatus.inactive);
        }

        @Test
        @DisplayName("should reject transition when already INACTIVE")
        void shouldRejectTransition_whenAlreadyInactive() {
            mockInstitution.setStatus(InstitutionStatus.inactive);
            when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(mockInstitution));

            assertThatThrownBy(() -> institutionService.transitionToInactive(institutionId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("active or pending");
        }
    }
}
