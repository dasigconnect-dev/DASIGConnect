package com.dasigconnect.backend.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.dasigconnect.backend.model.dto.institution.CreateInstitutionRequest;
import com.dasigconnect.backend.model.dto.institution.UpdateInstitutionRequest;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.InstitutionStatus;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.InvitationTokenRepository;
import com.dasigconnect.backend.repository.MediaAlbumRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.PageSettingsRepository;
import com.dasigconnect.backend.repository.SlotReservationRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.repository.WatermarkConfigurationRepository;
import com.dasigconnect.backend.security.JwtUserDetails;

/**
 * Validation telemetry: institution create / edit / deactivate / reactivate must
 * be attributed to the Administrator who performed them (previously these were
 * written as anonymous system entries, so they could not be tied to a
 * participant).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstitutionServiceAuditAttributionTest {

    @Mock private InstitutionRepository institutionRepository;
    @Mock private UserRepository userRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private MediaAssetRepository mediaAssetRepository;
    @Mock private MediaAlbumRepository mediaAlbumRepository;
    @Mock private InvitationTokenRepository invitationTokenRepository;
    @Mock private SlotReservationRepository slotReservationRepository;
    @Mock private PageSettingsRepository pageSettingsRepository;
    @Mock private WatermarkConfigurationRepository watermarkConfigurationRepository;
    @Mock private MediaStorageService mediaStorage;
    @Mock private WorkspaceProvisionerService workspaceProvisioner;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private InstitutionService institutionService;

    private UUID adminId;
    private UUID institutionId;
    private Institution institution;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        institutionId = UUID.randomUUID();
        institution = new Institution();
        institution.setId(institutionId);
        institution.setName("Cebu Institute of Technology - University");
        institution.setCode("CIT-U");
        institution.setEmailDomain("cit.edu");
        institution.setStatus(InstitutionStatus.active);
        when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(institution));
        when(institutionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        authenticateAs(adminId);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(UUID userId) {
        JwtUserDetails principal = new JwtUserDetails(userId, "admin@dasig.gov.ph", "admin", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of()));
    }

    @Test
    @DisplayName("createInstitution is attributed to the acting admin")
    void create_isAttributedToAdmin() {
        when(institutionRepository.existsByCode(any())).thenReturn(false);
        when(institutionRepository.existsByEmailDomain(any())).thenReturn(false);

        institutionService.createInstitution(new CreateInstitutionRequest("New Uni", "NU", "nu.edu.ph"));

        verify(auditLogService).recordByActorId(
                eq(adminId), eq("INSTITUTION_CREATED"), any(), any(), any(), org.mockito.ArgumentMatchers.<Map<String, ?>>any());
        verify(auditLogService, never()).recordSystemAction(eq("INSTITUTION_CREATED"), any(), any());
    }

    @Test
    @DisplayName("updateInstitution is attributed to the acting admin")
    void update_isAttributedToAdmin() {
        UpdateInstitutionRequest request = new UpdateInstitutionRequest();
        request.setName("Renamed University");
        request.setEmailDomain("renamed.edu.ph");

        institutionService.updateInstitution(institutionId, request);

        verify(auditLogService).recordByActorId(
                eq(adminId), eq("INSTITUTION_UPDATED"), any(), any(), eq(institutionId),
                org.mockito.ArgumentMatchers.<Map<String, ?>>any());
    }

    @Test
    @DisplayName("deactivateInstitution is attributed to the acting admin")
    void deactivate_isAttributedToAdmin() {
        institutionService.deactivateInstitution(institutionId);

        verify(auditLogService).recordByActorId(
                eq(adminId), eq("INSTITUTION_DEACTIVATED"), any(), any(), eq(institutionId),
                org.mockito.ArgumentMatchers.<Map<String, ?>>any());
    }

    @Test
    @DisplayName("reactivateInstitution is attributed to the acting admin")
    void reactivate_isAttributedToAdmin() {
        institution.setStatus(InstitutionStatus.inactive);

        institutionService.reactivateInstitution(institutionId);

        verify(auditLogService).recordByActorId(
                eq(adminId), eq("INSTITUTION_REACTIVATED"), any(), any(), eq(institutionId),
                org.mockito.ArgumentMatchers.<Map<String, ?>>any());
    }

    @Test
    @DisplayName("falls back to a system entry when there is no authenticated principal")
    void noPrincipal_fallsBackToSystemEntry() {
        SecurityContextHolder.clearContext();

        institutionService.deactivateInstitution(institutionId);

        verify(auditLogService).recordSystemAction(
                eq("INSTITUTION_DEACTIVATED"), eq(institutionId), org.mockito.ArgumentMatchers.<Map<String, ?>>any());
        verify(auditLogService, never()).recordByActorId(any(), any(), any(), any(), any(), any());
    }
}
