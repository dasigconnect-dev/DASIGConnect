package com.dasigconnect.backend.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.model.entity.MediaProcessingJob;
import com.dasigconnect.backend.model.entity.MediaProcessingJobType;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.AuditLogService;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.MediaProcessingQueueService;
import com.dasigconnect.backend.service.TenantScopeService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MediaProcessingAdminController.class)
@Import(SecurityConfig.class)
class MediaProcessingAdminControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MediaProcessingQueueService queueService;
    @MockitoBean private AuditLogService auditLogService;
    @MockitoBean private JWTService jwtService;
    @MockitoBean private TenantScopeService tenantScopeService;

    @Test
    void deadLetters_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/system-health/media-processing/dead-letters"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CONTRIBUTOR")
    void deadLetters_asContributor_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/system-health/media-processing/dead-letters"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deadLetters_asAdmin_returnsMappedJobs() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        MediaProcessingJob job = org.mockito.Mockito.mock(MediaProcessingJob.class);
        when(job.getId()).thenReturn(jobId);
        when(job.getAssetId()).thenReturn(assetId);
        when(job.getJobType()).thenReturn(MediaProcessingJobType.EMBED_IMAGE_ONLY);
        when(job.getProcessingVersion()).thenReturn("v2");
        when(job.getAttemptCount()).thenReturn(3);
        when(job.getMaxAttempts()).thenReturn(3);
        when(job.getLastError()).thenReturn("provider timeout");
        when(job.getUpdatedAt()).thenReturn(Instant.parse("2026-10-16T08:00:00Z"));
        when(queueService.deadLetters(10)).thenReturn(List.of(job));

        mockMvc.perform(get("/api/v1/system-health/media-processing/dead-letters")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(jobId.toString()))
                .andExpect(jsonPath("$.data[0].assetId").value(assetId.toString()))
                .andExpect(jsonPath("$.data[0].jobType").value("EMBED_IMAGE_ONLY"))
                .andExpect(jsonPath("$.data[0].lastError").value("provider timeout"));

        verify(queueService).deadLetters(10);
    }

    @Test
    void retryDeadLetter_asAdmin_retriesAndAuditsActor() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/system-health/media-processing/dead-letters/{jobId}/retry", jobId)
                        .with(authentication(adminAuth(actorId))))
                .andExpect(status().isNoContent());

        verify(queueService).retryDead(jobId);
        verify(auditLogService).recordByActorId(
                actorId,
                "MEDIA_PROCESSING_DEAD_LETTER_RETRIED",
                null,
                null,
                jobId,
                Map.of("jobId", jobId));
    }

    private static UsernamePasswordAuthenticationToken adminAuth(UUID actorId) {
        JwtUserDetails principal = new JwtUserDetails(actorId, "admin@dasig.gov.ph", "admin", null);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
