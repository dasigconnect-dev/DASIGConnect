package com.dasigconnect.backend.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.model.dto.auth.LoginResponseDto;
import com.dasigconnect.backend.model.dto.invitation.InvitationResponseDto;
import com.dasigconnect.backend.model.dto.invitation.InvitationValidateResponseDto;
import com.dasigconnect.backend.model.dto.invitation.PendingInvitationDto;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.service.InvitationService;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.TenantScopeService;

@WebMvcTest(InvitationController.class)
@Import(SecurityConfig.class)
class InvitationControllerTest {

    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    InvitationService invitationService;
    @MockitoBean
    JWTService jwtService;
    @MockitoBean
    TenantScopeService tenantScopeService;

    private static final UUID INSTITUTION_ID = UUID.randomUUID();

    @Test
    void createInvitation_withoutAuth_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/invitations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"recipientEmail":"user@example.com","institutionId":"%s","assignedRole":"contributor"}
                                """.formatted(INSTITUTION_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createInvitation_asAdmin_returns201() throws Exception {
        InvitationResponseDto response = new InvitationResponseDto(
                UUID.randomUUID(), "user@example.com", UserRole.contributor,
                INSTITUTION_ID, Instant.now().plusSeconds(3600), Instant.now(),
                true, "http://localhost:5173/invite?token=token");
        when(invitationService.createInvitation(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/invitations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"recipientEmail":"user@example.com","institutionId":"%s","assignedRole":"contributor"}
                                """.formatted(INSTITUTION_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.recipientEmail").value("user@example.com"))
                .andExpect(jsonPath("$.data.assignedRole").value("contributor"))
                .andExpect(jsonPath("$.data.emailDelivered").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createInvitation_missingRecipientEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/invitations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"institutionId":"%s","assignedRole":"contributor"}
                                """.formatted(INSTITUTION_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.fields.recipientEmail").exists());
    }

    @Test
    @WithMockUser(roles = "CONTRIBUTOR")
    void createInvitation_asContributor_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/invitations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"recipientEmail":"user@example.com","institutionId":"%s","assignedRole":"contributor"}
                                """.formatted(INSTITUTION_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void createInvitation_asValidator_reachesService() throws Exception {
        InvitationResponseDto response = new InvitationResponseDto(
                UUID.randomUUID(), "user@example.com", UserRole.contributor,
                INSTITUTION_ID, Instant.now().plusSeconds(3600), Instant.now(),
                true, "http://localhost:5173/invite?token=token");
        when(invitationService.createInvitation(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientEmail":"user@example.com","institutionId":"%s","assignedRole":"contributor"}
                                """.formatted(INSTITUTION_ID)))
                .andExpect(status().isCreated());
    }

    @Test
    void validateToken_public_returns200() throws Exception {
        InvitationValidateResponseDto response = new InvitationValidateResponseDto(
                "user@example.com", UserRole.contributor, "CIT-U", Instant.now().plusSeconds(3600));
        when(invitationService.validateToken("sometoken")).thenReturn(response);

        mockMvc.perform(get("/api/v1/invitations/validate").param("token", "sometoken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recipientEmail").value("user@example.com"))
                .andExpect(jsonPath("$.data.institutionName").value("CIT-U"));
    }

    @Test
    void acceptInvitation_public_returns200() throws Exception {
        when(invitationService.acceptInvitation(any()))
                .thenReturn(new LoginResponseDto("new.jwt.token", "contributor", INSTITUTION_ID));

        mockMvc.perform(post("/api/v1/invitations/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"token":"sometoken","firstName":"Mark","lastName":"Camoro","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new.jwt.token"));
    }

    @Test
    void acceptInvitation_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/invitations/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"token":"sometoken","firstName":"Mark","lastName":"Camoro","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.fields.password").exists());
    }

    @Test
    void acceptInvitation_missingName_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/invitations/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"token":"sometoken","lastName":"Camoro","password":"password123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.fields.firstName").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void resend_asAdmin_returnsInvitation() throws Exception {
        UUID invitationId = UUID.randomUUID();
        InvitationResponseDto response = new InvitationResponseDto(
                UUID.randomUUID(), "user@example.com", UserRole.contributor,
                INSTITUTION_ID, Instant.now().plusSeconds(3600), Instant.now(),
                true, "http://localhost:5173/invite?token=new-token");
        when(invitationService.resend(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/invitations/{id}/resend", invitationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recipientEmail").value("user@example.com"))
                .andExpect(jsonPath("$.data.emailDelivered").value(true));
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void pending_asValidator_returnsPendingInvitations() throws Exception {
        UUID invitationId = UUID.randomUUID();
        when(invitationService.listPending(any(), any())).thenReturn(List.of(new PendingInvitationDto(
                invitationId,
                "user@example.com",
                UserRole.contributor,
                INSTITUTION_ID,
                Instant.now().plusSeconds(3600),
                Instant.now())));

        mockMvc.perform(get("/api/v1/invitations/pending").param("institutionId", INSTITUTION_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(invitationId.toString()))
                .andExpect(jsonPath("$.data[0].recipientEmail").value("user@example.com"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void pendingAdmins_asAdmin_returnsPendingAdminInvitations() throws Exception {
        UUID invitationId = UUID.randomUUID();
        when(invitationService.listPendingAdmins(any())).thenReturn(List.of(new PendingInvitationDto(
                invitationId,
                "admin@example.com",
                UserRole.admin,
                null,
                Instant.now().plusSeconds(3600),
                Instant.now())));

        mockMvc.perform(get("/api/v1/invitations/pending/admins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(invitationId.toString()))
                .andExpect(jsonPath("$.data[0].recipientEmail").value("admin@example.com"))
                .andExpect(jsonPath("$.data[0].institutionId").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void pendingCount_asValidator_returnsCount() throws Exception {
        when(invitationService.countPending(any(), any())).thenReturn(Map.of("pendingInvitations", 3L));

        mockMvc.perform(get("/api/v1/invitations/pending/count").param("institutionId", INSTITUTION_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingInvitations").value(3));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void cancelByUser_asAdmin_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/invitations/by-user/{userId}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void cancelByUser_asModerator_isForbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/invitations/by-user/{userId}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }
}
