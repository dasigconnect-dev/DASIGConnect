package com.dasigconnect.backend.controller;

import java.time.Instant;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.model.dto.auth.LoginResponseDto;
import com.dasigconnect.backend.model.dto.invitation.InvitationResponseDto;
import com.dasigconnect.backend.model.dto.invitation.InvitationValidateResponseDto;
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
    @WithMockUser(roles = "ADMINISTRATOR")
    void createInvitation_asAdministrator_returns201() throws Exception {
        InvitationResponseDto response = new InvitationResponseDto(
                UUID.randomUUID(), "user@example.com", UserRole.contributor,
                INSTITUTION_ID, Instant.now().plusSeconds(3600), Instant.now());
        when(invitationService.createInvitation(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/invitations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"recipientEmail":"user@example.com","institutionId":"%s","assignedRole":"contributor"}
                                """.formatted(INSTITUTION_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recipientEmail").value("user@example.com"))
                .andExpect(jsonPath("$.assignedRole").value("contributor"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void createInvitation_missingRecipientEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/invitations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"institutionId":"%s","assignedRole":"contributor"}
                                """.formatted(INSTITUTION_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.recipientEmail").exists());
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
    void validateToken_public_returns200() throws Exception {
        InvitationValidateResponseDto response = new InvitationValidateResponseDto(
                "user@example.com", UserRole.contributor, "CIT-U", Instant.now().plusSeconds(3600));
        when(invitationService.validateToken("sometoken")).thenReturn(response);

        mockMvc.perform(get("/api/v1/invitations/validate").param("token", "sometoken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientEmail").value("user@example.com"))
                .andExpect(jsonPath("$.institutionName").value("CIT-U"));
    }

    @Test
    void acceptInvitation_public_returns200() throws Exception {
        when(invitationService.acceptInvitation(any()))
                .thenReturn(new LoginResponseDto("new.jwt.token", "contributor", INSTITUTION_ID));

        mockMvc.perform(post("/api/v1/invitations/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"token":"sometoken","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new.jwt.token"));
    }

    @Test
    void acceptInvitation_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/invitations/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"token":"sometoken","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").exists());
    }
}
