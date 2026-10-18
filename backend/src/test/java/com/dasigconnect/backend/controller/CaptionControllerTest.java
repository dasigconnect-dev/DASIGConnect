package com.dasigconnect.backend.controller;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.external.ClaudeVisionClient;
import com.dasigconnect.backend.model.dto.ai.CaptionResponseDto;
import com.dasigconnect.backend.model.dto.ai.CaptionVariantDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.CaptionGenerationService;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.SubscriptionEntitlementService;
import com.dasigconnect.backend.service.TenantScopeService;
import com.dasigconnect.backend.model.entity.SubscriptionUsageEventType;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CaptionController.class)
@Import(SecurityConfig.class)
class CaptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private CaptionGenerationService captionGenerationService;
    @MockitoBean private JWTService jwtService;
    @MockitoBean private TenantScopeService tenantScopeService;
    @MockitoBean private SubscriptionEntitlementService subscriptionEntitlementService;

    private UUID reservationId;

    @BeforeEach
    void setUpEntitlementReservation() {
        reservationId = UUID.randomUUID();
        when(subscriptionEntitlementService.reserveForSubmission(
                ArgumentMatchers.any(UUID.class),
                ArgumentMatchers.any(UUID.class),
                ArgumentMatchers.eq(SubscriptionUsageEventType.AI_CAPTION),
                ArgumentMatchers.nullable(String.class),
                ArgumentMatchers.eq("AI_CAPTION_REQUEST")))
                .thenReturn(new SubscriptionEntitlementService.ActionReservation(
                        reservationId, 4, Instant.now().plusSeconds(3600), false, false));
    }

    // ── POST /caption ─────────────────────────────────────────────────────────

    @Test
    void generateCaption_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/ai/caption")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"submissionId":"%s"}
                        """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void generateCaption_asContributor_returnsThreeVariants() throws Exception {
        UUID submissionId = UUID.randomUUID();
        List<CaptionVariantDto> variants = List.of(
                new CaptionVariantDto("professional", "Professional caption #DASIG #Innovation"),
                new CaptionVariantDto("community", "Community caption #DASIG #Together"),
                new CaptionVariantDto("energetic", "Energetic caption! #DASIG #Energy")
        );
        when(captionGenerationService.generateCaptions(
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(new CaptionResponseDto(submissionId, variants));

        mockMvc.perform(post("/api/v1/ai/caption")
                .with(authentication(contributorAuth()))
                .header("X-Idempotency-Key", "caption-request-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"submissionId":"%s"}
                        """.formatted(submissionId)))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-RateLimit-Remaining"))
                .andExpect(header().exists("X-RateLimit-Reset"))
                .andExpect(jsonPath("$.data.submissionId").value(submissionId.toString()))
                .andExpect(jsonPath("$.data.variants.length()").value(3))
                .andExpect(jsonPath("$.data.variants[0].tone").value("professional"))
                .andExpect(jsonPath("$.data.variants[1].tone").value("community"))
                .andExpect(jsonPath("$.data.variants[2].tone").value("energetic"));

        verify(subscriptionEntitlementService).consume(reservationId);
    }

    @Test
    void generateCaption_asModerator_returnsVariants() throws Exception {
        UUID submissionId = UUID.randomUUID();
        when(captionGenerationService.generateCaptions(
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(new CaptionResponseDto(submissionId, List.of(
                        new CaptionVariantDto("professional", "Caption #DASIG")
                )));

        mockMvc.perform(post("/api/v1/ai/caption")
                .with(authentication(adminAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"submissionId":"%s","existingCaption":"Draft caption"}
                        """.formatted(submissionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.variants[0].caption").value("Caption #DASIG"));
    }

    @Test
    void generateCaption_asModeratorRole_isAllowed() throws Exception {
        UUID submissionId = UUID.randomUUID();
        when(captionGenerationService.generateCaptions(
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(new CaptionResponseDto(submissionId, List.of(
                        new CaptionVariantDto("professional", "Caption #DASIG")
                )));

        mockMvc.perform(post("/api/v1/ai/caption")
                .with(authentication(moderatorAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"submissionId":"%s","existingCaption":"Draft caption"}
                        """.formatted(submissionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.variants[0].caption").value("Caption #DASIG"));
    }

    @Test
    void generateCaption_missingSubmissionId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/ai/caption")
                .with(authentication(contributorAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generateCaption_claudeTimeout_returns504() throws Exception {
        when(captionGenerationService.generateCaptions(
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenThrow(new ClaudeVisionClient.ClaudeApiException("Claude API timed out after 10 seconds."));

        mockMvc.perform(post("/api/v1/ai/caption")
                .with(authentication(contributorAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"submissionId":"%s"}
                        """.formatted(UUID.randomUUID())))
                .andExpect(status().isGatewayTimeout());

        verify(subscriptionEntitlementService).release(reservationId);
    }

    @Test
    void generateCaption_claudeUnavailable_returns503() throws Exception {
        when(captionGenerationService.generateCaptions(
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenThrow(new ClaudeVisionClient.ClaudeApiException("Claude API error (HTTP 500)."));

        mockMvc.perform(post("/api/v1/ai/caption")
                .with(authentication(contributorAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"submissionId":"%s"}
                        """.formatted(UUID.randomUUID())))
                .andExpect(status().isServiceUnavailable());
    }

    // ── POST /caption/log ─────────────────────────────────────────────────────

    @Test
    void logCaptionInteraction_asContributor_returns204() throws Exception {
        UUID submissionId = UUID.randomUUID();
        doNothing().when(captionGenerationService).logInteraction(
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());

        mockMvc.perform(post("/api/v1/ai/caption/log")
                .with(authentication(contributorAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"submissionId":"%s","actionTaken":"use","toneSelected":"professional"}
                        """.formatted(submissionId)))
                .andExpect(status().isNoContent());

        verify(captionGenerationService).logInteraction(
                ArgumentMatchers.eq(submissionId),
                ArgumentMatchers.any(),
                ArgumentMatchers.eq("use"),
                ArgumentMatchers.eq("professional"),
                ArgumentMatchers.isNull(),
                ArgumentMatchers.isNull());
    }

    @Test
    void logCaptionInteraction_withQualityRatingAndDraftingDuration_passesTelemetry() throws Exception {
        UUID submissionId = UUID.randomUUID();
        doNothing().when(captionGenerationService).logInteraction(
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());

        mockMvc.perform(post("/api/v1/ai/caption/log")
                .with(authentication(contributorAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"submissionId":"%s","actionTaken":"use_then_edited","toneSelected":"community","qualityRating":5,"draftingDurationMs":14250}
                        """.formatted(submissionId)))
                .andExpect(status().isNoContent());

        verify(captionGenerationService).logInteraction(
                ArgumentMatchers.eq(submissionId),
                ArgumentMatchers.any(),
                ArgumentMatchers.eq("use_then_edited"),
                ArgumentMatchers.eq("community"),
                ArgumentMatchers.eq(5),
                ArgumentMatchers.eq(14250L));
    }

    @Test
    void generateCaption_backgroundEndpoint_returnsVariant() throws Exception {
        UUID submissionId = UUID.randomUUID();
        when(captionGenerationService.generateCaptions(
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(new CaptionResponseDto(submissionId, List.of(
                        new CaptionVariantDto("professional", "Auto-generated background caption #DASIG")
                )));

        mockMvc.perform(post("/api/v1/ai/caption/background")
                .with(authentication(contributorAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"submissionId":"%s","tone":"professional"}
                        """.formatted(submissionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissionId").value(submissionId.toString()))
                .andExpect(jsonPath("$.data.variants[0].caption").value("Auto-generated background caption #DASIG"));
    }

    @Test
    void logCaptionInteraction_missingActionTaken_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/ai/caption/log")
                .with(authentication(contributorAuth()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"submissionId":"%s"}
                        """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static UsernamePasswordAuthenticationToken contributorAuth() {
        JwtUserDetails principal = new JwtUserDetails(
                UUID.randomUUID(), "contributor@cit.edu.ph", "contributor", UUID.randomUUID());
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_CONTRIBUTOR")));
    }

    private static UsernamePasswordAuthenticationToken adminAuth() {
        JwtUserDetails principal = new JwtUserDetails(
                UUID.randomUUID(), "admin@dasig.gov.ph", "admin", null);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private static UsernamePasswordAuthenticationToken moderatorAuth() {
        JwtUserDetails principal = new JwtUserDetails(
                UUID.randomUUID(), "moderator@dasig.gov.ph", "moderator", null);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_MODERATOR")));
    }
}
