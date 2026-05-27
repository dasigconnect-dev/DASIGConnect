package com.dasigconnect.backend.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionMediaAsset;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.repository.PublicationAttemptRepository;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.ManualPublishingService;
import com.dasigconnect.backend.service.TenantScopeService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResolutionController.class)
@Import(SecurityConfig.class)
class ResolutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private SubmissionRepository submissionRepository;
    @MockitoBean private PublicationAttemptRepository publicationAttemptRepository;
    @MockitoBean private SubmissionMediaAssetRepository submissionMediaAssetRepository;
    @MockitoBean private ManualPublishingService manualPublishingService;
    @MockitoBean private JWTService jwtService;
    @MockitoBean private TenantScopeService tenantScopeService;

    // ── GET /failures ─────────────────────────────────────────────────────────

    @Test
    void listFailures_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/resolution/failures"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CONTRIBUTOR")
    void listFailures_asContributor_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/resolution/failures"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void listFailures_asAdministrator_returnsEmptyList() throws Exception {
        when(submissionRepository.findPublishFailures()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/resolution/failures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void listFailures_asAdministrator_returnsFailureList() throws Exception {
        Submission s = publishFailedSubmission(UUID.randomUUID());
        when(submissionRepository.findPublishFailures()).thenReturn(List.of(s));
        when(publicationAttemptRepository.findTopBySubmissionIdOrderByAttemptedAtDesc(any()))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/resolution/failures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].submissionId").value(s.getId().toString()))
                .andExpect(jsonPath("$[0].eventTitle").value("Tech Summit 2026"));
    }

    // ── GET /{id} ─────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void getDetail_publishFailed_returnsDetail() throws Exception {
        UUID id = UUID.randomUUID();
        Submission s = publishFailedSubmission(id);
        SubmissionMediaAsset sma = mediaAssetJunction(s, mediaAsset(UUID.randomUUID()));
        when(submissionRepository.findById(id)).thenReturn(Optional.of(s));
        when(submissionMediaAssetRepository.findBySubmissionIdWithMediaAsset(id))
                .thenReturn(List.of(sma));

        mockMvc.perform(get("/api/v1/resolution/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionId").value(id.toString()))
                .andExpect(jsonPath("$.eventTitle").value("Tech Summit 2026"))
                .andExpect(jsonPath("$.status").value("publish_failed"))
                .andExpect(jsonPath("$.mediaAssets").isArray())
                .andExpect(jsonPath("$.mediaAssets.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void getDetail_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(submissionRepository.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/resolution/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void getDetail_ineligibleStatus_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        Submission s = publishFailedSubmission(id);
        s.setStatus(SubmissionStatus.pending);
        when(submissionRepository.findById(id)).thenReturn(Optional.of(s));

        mockMvc.perform(get("/api/v1/resolution/{id}", id))
                .andExpect(status().isConflict());
    }

    // ── POST /{id}/retry ──────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void retry_asAdministrator_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(manualPublishingService).retry(eq(id), any());

        mockMvc.perform(post("/api/v1/resolution/{id}/retry", id))
                .andExpect(status().isNoContent());

        verify(manualPublishingService).retry(eq(id), any());
    }

    // ── POST /{id}/manual-publish/start ───────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void startManualPublish_asAdministrator_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(manualPublishingService).start(eq(id), any());

        mockMvc.perform(post("/api/v1/resolution/{id}/manual-publish/start", id))
                .andExpect(status().isNoContent());

        verify(manualPublishingService).start(eq(id), any());
    }

    // ── POST /{id}/manual-publish/complete ────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void completeManualPublish_withUrl_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(manualPublishingService).complete(eq(id), any(), any());

        mockMvc.perform(post("/api/v1/resolution/{id}/manual-publish/complete", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"postUrl":"https://www.facebook.com/dasig/posts/1","notes":"Done"}
                        """))
                .andExpect(status().isNoContent());

        verify(manualPublishingService).complete(eq(id), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void completeManualPublish_withoutBody_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(manualPublishingService).complete(eq(id), any(), any());

        mockMvc.perform(post("/api/v1/resolution/{id}/manual-publish/complete", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isNoContent());
    }

    // ── POST /{id}/manual-publish/cancel ──────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void cancelManualPublish_asAdministrator_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(manualPublishingService).cancel(eq(id), any());

        mockMvc.perform(post("/api/v1/resolution/{id}/manual-publish/cancel", id))
                .andExpect(status().isNoContent());

        verify(manualPublishingService).cancel(eq(id), any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Submission publishFailedSubmission(UUID id) {
        Institution institution = new Institution();
        institution.setId(UUID.randomUUID());
        institution.setName("CIT-U");
        institution.setCode("CIT-U");
        institution.setEmailDomain("cit.edu.ph");

        User contributor = new User();
        contributor.setId(UUID.randomUUID());
        contributor.setEmail("contributor@cit.edu.ph");
        contributor.setFirstName("Maria");
        contributor.setLastName("Santos");
        contributor.setRole(UserRole.contributor);

        Submission s = new Submission();
        s.setId(id);
        s.setEventTitle("Tech Summit 2026");
        s.setEventDate(LocalDate.of(2026, 6, 15));
        s.setCaption("Join us for the Tech Summit!");
        s.setStatus(SubmissionStatus.publish_failed);
        s.setScheduledAt(Instant.parse("2026-06-14T10:00:00Z"));
        s.setContributor(contributor);
        s.setInstitution(institution);
        return s;
    }

    private static MediaAsset mediaAsset(UUID id) {
        MediaAsset a = new MediaAsset();
        a.setId(id);
        a.setStorageUrl("https://storage.example.com/photo.jpg");
        a.setFileName("photo.jpg");
        a.setFileType(MediaFileType.jpeg);
        a.setFileSizeBytes(102400L);
        return a;
    }

    private static SubmissionMediaAsset mediaAssetJunction(Submission s, MediaAsset a) {
        SubmissionMediaAsset sma = new SubmissionMediaAsset();
        sma.setId(UUID.randomUUID());
        sma.setSubmission(s);
        sma.setMediaAsset(a);
        sma.setDisplayOrder(0);
        return sma;
    }
}
