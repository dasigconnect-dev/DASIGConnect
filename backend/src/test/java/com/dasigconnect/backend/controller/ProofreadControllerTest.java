package com.dasigconnect.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.external.ClaudeVisionClient;
import com.dasigconnect.backend.model.dto.ai.ProofreadIssueDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.AiAdoptionTrackingService;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.ProofreadService;
import com.dasigconnect.backend.service.TenantScopeService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProofreadController.class)
@Import(SecurityConfig.class)
class ProofreadControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ProofreadService proofreadService;
    @MockitoBean private AiAdoptionTrackingService adoptionTrackingService;
    @MockitoBean private JWTService jwtService;
    @MockitoBean private TenantScopeService tenantScopeService;

    @Test
    void proofread_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/ai/proofread")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Caption text\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void proofread_withUnsupportedRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/ai/proofread")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Caption text\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void proofread_withBlankText_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/ai/proofread")
                        .with(authentication(auth("contributor", UUID.randomUUID())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void proofread_success_returnsIssuesAndTracksCheck() throws Exception {
        UUID institutionId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UsernamePasswordAuthenticationToken auth = auth("contributor", institutionId);
        when(proofreadService.proofread("We was ready.", "We were ready."))
                .thenReturn(List.of(new ProofreadIssueDto(
                        "grammar", "was", "were", "Use subject-verb agreement.")));

        mockMvc.perform(post("/api/v1/ai/proofread")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"We was ready.","originalText":"We were ready.",
                                 "submissionId":"%s"}
                                """.formatted(submissionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.issues[0].kind").value("grammar"))
                .andExpect(jsonPath("$.data.issues[0].suggestion").value("were"));

        verify(adoptionTrackingService).recordProofreadCheck(submissionId, institutionId, 1);
    }

    @Test
    void proofread_trackingFailure_doesNotFailResponse() throws Exception {
        UUID institutionId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        when(proofreadService.proofread(any(), any())).thenReturn(List.of());
        doThrow(new RuntimeException("analytics unavailable"))
                .when(adoptionTrackingService).recordProofreadCheck(submissionId, institutionId, 0);

        mockMvc.perform(post("/api/v1/ai/proofread")
                        .with(authentication(auth("admin", institutionId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"Caption text","submissionId":"%s"}
                                """.formatted(submissionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.issues").isEmpty());
    }

    @Test
    void proofread_providerTimeout_returns504() throws Exception {
        when(proofreadService.proofread(any(), any())).thenThrow(
                new ClaudeVisionClient.ClaudeApiException("Claude API timed out after 30 seconds."));

        mockMvc.perform(post("/api/v1/ai/proofread")
                        .with(authentication(auth("moderator", null)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Caption text\"}"))
                .andExpect(status().isGatewayTimeout());
    }

    @Test
    void proofread_providerError_returns503() throws Exception {
        when(proofreadService.proofread(any(), any())).thenThrow(
                new ClaudeVisionClient.ClaudeApiException("Claude API error (HTTP 500)."));

        mockMvc.perform(post("/api/v1/ai/proofread")
                        .with(authentication(auth("contributor", UUID.randomUUID())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Caption text\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void fixApplied_recordsUsageAndReturns204() throws Exception {
        UUID institutionId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/ai/proofread/applied")
                        .with(authentication(auth("contributor", institutionId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"submissionId\":\"%s\"}".formatted(submissionId)))
                .andExpect(status().isNoContent());

        verify(adoptionTrackingService).recordProofreadFixApplied(submissionId, institutionId);
    }

    @Test
    void fixApplied_trackingFailure_stillReturns204() throws Exception {
        UUID institutionId = UUID.randomUUID();
        doThrow(new RuntimeException("analytics unavailable"))
                .when(adoptionTrackingService).recordProofreadFixApplied(eq(null), eq(institutionId));

        mockMvc.perform(post("/api/v1/ai/proofread/applied")
                        .with(authentication(auth("admin", institutionId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNoContent());
    }

    private static UsernamePasswordAuthenticationToken auth(String role, UUID institutionId) {
        JwtUserDetails principal = new JwtUserDetails(
                UUID.randomUUID(), role + "@example.com", role, institutionId);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
    }
}
