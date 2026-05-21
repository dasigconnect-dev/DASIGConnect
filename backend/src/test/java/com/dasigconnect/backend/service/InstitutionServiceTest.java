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

/**
 * Unit tests for InstitutionService.
 *
 * Tests institution creation, state machine transitions, and 404 behavior.
 *
 * Place at:
 * backend/src/test/java/com/dasigconnect/backend/service/InstitutionServiceTest.java
 */
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
        mockInstitution.setStatus(InstitutionStatus.onboarding);
    }

    // ── createInstitution ─────────────────────────────────────────────────────
    @Nested
    @DisplayName("createInstitution()")
    class CreateInstitutionTests {

        @Test
        @DisplayName("should create institution with ONBOARDING status")
        void shouldCreateInstitution_withOnboardingStatus() {
            when(institutionRepository.existsByCode("CIT-U")).thenReturn(false);
            when(institutionRepository.save(any())).thenReturn(mockInstitution);

            CreateInstitutionRequest req = new CreateInstitutionRequest("Cebu Institute of Technology - University", "CIT-U", "cit.edu.ph");
            InstitutionDto result = institutionService.createInstitution(req);

            assertThat(result.getStatus()).isEqualTo(InstitutionStatus.onboarding);
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

    // ── State machine transitions ─────────────────────────────────────────────
    @Nested
    @DisplayName("transitionToActive() — ONBOARDING → ACTIVE")
    class TransitionToActiveTests {

        @Test
        @DisplayName("should transition from ONBOARDING to ACTIVE")
        void shouldTransition_fromOnboardingToActive() {
            when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(mockInstitution));
            when(institutionRepository.save(any())).thenReturn(mockInstitution);

            institutionService.transitionToActive(institutionId);

            assertThat(mockInstitution.getStatus()).isEqualTo(InstitutionStatus.active);
            verify(institutionRepository).save(mockInstitution);
        }

        @Test
        @DisplayName("should write audit log on transition")
        void shouldWriteAuditLog_onActivation() {
            when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(mockInstitution));
            when(institutionRepository.save(any())).thenReturn(mockInstitution);

            institutionService.transitionToActive(institutionId);

            verify(auditLogService).recordSystemAction(
                    eq("INSTITUTION_ACTIVATED"), eq(institutionId), org.mockito.ArgumentMatchers.<Map<String, ?>>any());
        }

        @Test
        @DisplayName("should reject transition when not in ONBOARDING state")
        void shouldRejectTransition_whenNotOnboarding() {
            mockInstitution.setStatus(InstitutionStatus.active); // already active
            when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(mockInstitution));

            assertThatThrownBy(() -> institutionService.transitionToActive(institutionId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("onboarding");
        }
    }

    @Nested
    @DisplayName("transitionToInactiveNoValidator() — ACTIVE → INACTIVE_NO_VALIDATOR")
    class TransitionToInactiveTests {

        @BeforeEach
        void setActive() {
            mockInstitution.setStatus(InstitutionStatus.active);
        }

        @Test
        @DisplayName("should transition from ACTIVE to INACTIVE_NO_VALIDATOR")
        void shouldTransition_fromActiveToInactive() {
            when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(mockInstitution));
            when(institutionRepository.save(any())).thenReturn(mockInstitution);

            institutionService.transitionToInactiveNoValidator(institutionId);

            assertThat(mockInstitution.getStatus()).isEqualTo(InstitutionStatus.inactive_no_validator);
        }

        @Test
        @DisplayName("should reject transition when not in ACTIVE state")
        void shouldRejectTransition_whenNotActive() {
            mockInstitution.setStatus(InstitutionStatus.onboarding);
            when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(mockInstitution));

            assertThatThrownBy(() -> institutionService.transitionToInactiveNoValidator(institutionId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("active");
        }
    }

    @Nested
    @DisplayName("reactivate() — INACTIVE_NO_VALIDATOR → ACTIVE")
    class ReactivateTests {

        @BeforeEach
        void setInactive() {
            mockInstitution.setStatus(InstitutionStatus.inactive_no_validator);
        }

        @Test
        @DisplayName("should reactivate from INACTIVE_NO_VALIDATOR to ACTIVE")
        void shouldReactivate() {
            when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(mockInstitution));
            when(institutionRepository.save(any())).thenReturn(mockInstitution);

            institutionService.reactivate(institutionId);

            assertThat(mockInstitution.getStatus()).isEqualTo(InstitutionStatus.active);
        }

        @Test
        @DisplayName("should reject reactivation when not in INACTIVE_NO_VALIDATOR state")
        void shouldRejectReactivation_whenNotInactive() {
            mockInstitution.setStatus(InstitutionStatus.active);
            when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(mockInstitution));

            assertThatThrownBy(() -> institutionService.reactivate(institutionId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("inactive_no_validator");
        }
    }
}
