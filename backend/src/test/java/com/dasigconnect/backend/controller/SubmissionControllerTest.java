package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.exception.GuardRailViolationException;
import com.dasigconnect.backend.model.dto.guardrail.GuardRailResult;
import com.dasigconnect.backend.model.dto.guardrail.GuardRailViolation;
import com.dasigconnect.backend.model.dto.submission.SubmissionResponseDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionSummaryDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.model.entity.UserStatus;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.SubmissionService;
import com.dasigconnect.backend.service.TenantScopeService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubmissionController.class)
@Import(SecurityConfig.class)
class SubmissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubmissionService submissionService;

    @MockitoBean
    private JWTService jwtService;

    @MockitoBean
    private TenantScopeService tenantScopeService;

    @Test
    @WithMockUser
    void lookups_authenticated_returnsReferenceData() throws Exception {
        mockMvc.perform(get("/api/v1/submissions/lookups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedFileTypes").isArray())
                .andExpect(jsonPath("$.maxMediaAssetsPerSubmission").exists());
    }

    @Test
    @WithMockUser
    void list_authenticated_returnsRoleFilteredSubmissions() throws Exception {
        when(submissionService.list(any())).thenReturn(List.of(summaryDto(UUID.randomUUID())));

        mockMvc.perform(get("/api/v1/submissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventTitle").value("Research Expo"))
                .andExpect(jsonPath("$[0].mediaCount").value(2));
    }

    @Test
    @WithMockUser(roles = "CONTRIBUTOR")
    void create_asContributor_returns201() throws Exception {
        when(submissionService.create(any(), any())).thenReturn(responseDto(UUID.randomUUID(), SubmissionStatus.draft));

        mockMvc.perform(post("/api/v1/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"eventTitle":"Research Expo","eventDate":"2026-06-01","caption":"Caption"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.eventTitle").value("Research Expo"));
    }

    @Test
    @WithMockUser(roles = "VALIDATOR")
    void create_asValidator_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"eventTitle":"Research Expo","eventDate":"2026-06-01"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CONTRIBUTOR")
    void create_missingEventTitle_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"eventDate":"2026-06-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.eventTitle").exists());
    }

    @Test
    @WithMockUser
    void get_authenticated_returnsSubmissionDetail() throws Exception {
        UUID submissionId = UUID.randomUUID();
        when(submissionService.get(any(), any())).thenReturn(responseDto(submissionId, SubmissionStatus.pending));

        mockMvc.perform(get("/api/v1/submissions/{id}", submissionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(submissionId.toString()))
                .andExpect(jsonPath("$.status").value("pending"));
    }

    @Test
    @WithMockUser(roles = "CONTRIBUTOR")
    void update_asContributor_returnsUpdatedSubmission() throws Exception {
        UUID submissionId = UUID.randomUUID();
        when(submissionService.update(any(), any(), any())).thenReturn(responseDto(submissionId, SubmissionStatus.draft));

        mockMvc.perform(patch("/api/v1/submissions/{id}", submissionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"eventTitle":"Updated Expo"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(submissionId.toString()));
    }

    @Test
    @WithMockUser(roles = "CONTRIBUTOR")
    void update_guardRailViolation_returns422WithViolations() throws Exception {
        UUID submissionId = UUID.randomUUID();
        when(submissionService.update(any(), any(), any()))
                .thenThrow(new GuardRailViolationException(List.of(
                        new GuardRailViolation("GR-H2", "Scheduled time must be at least 2 hours from now."))));

        mockMvc.perform(patch("/api/v1/submissions/{id}", submissionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"scheduledAt":"2026-06-01T08:00:00Z"}
                                """))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Scheduled time must be at least 2 hours from now."))
                .andExpect(jsonPath("$.summary").value("Slot rejected: GR-H2"))
                .andExpect(jsonPath("$.violations[0].code").value("GR-H2"));
    }

    @Test
    @WithMockUser(roles = "CONTRIBUTOR")
    void delete_asContributor_returns204() throws Exception {
        UUID submissionId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/submissions/{id}", submissionId))
                .andExpect(status().isNoContent());

        verify(submissionService).delete(any(), any());
    }

    @Test
    @WithMockUser(roles = "CONTRIBUTOR")
    void submit_asContributor_returnsPendingSubmission() throws Exception {
        UUID submissionId = UUID.randomUUID();
        when(submissionService.submit(any(), any())).thenReturn(responseDto(submissionId, SubmissionStatus.pending));

        mockMvc.perform(post("/api/v1/submissions/{id}/submit", submissionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending"));
    }

    @Test
    @WithMockUser(roles = "CONTRIBUTOR")
    void evaluateSlot_validBody_returnsGuardRailResult() throws Exception {
        UUID submissionId = UUID.randomUUID();
        when(submissionService.evaluateSlot(any(), any())).thenReturn(new GuardRailResult());

        mockMvc.perform(post("/api/v1/submissions/{id}/evaluate-slot", submissionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"scheduledAt":"2026-06-01T08:00:00Z"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(false))
                .andExpect(jsonPath("$.clean").value(true));
    }

    @Test
    @WithMockUser(roles = "CONTRIBUTOR")
    void attachMedia_validBody_returns201() throws Exception {
        UUID submissionId = UUID.randomUUID();
        when(submissionService.attachMedia(any(), any(), any())).thenReturn(responseDto(submissionId, SubmissionStatus.draft));

        mockMvc.perform(post("/api/v1/submissions/{id}/media", submissionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"storageUrl":"https://storage.example/photo.jpg","fileName":"photo.jpg","fileType":"jpeg","fileSizeBytes":1024}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(submissionId.toString()));
    }

    @Test
    @WithMockUser(roles = "CONTRIBUTOR")
    void attachMedia_missingStorageUrl_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/submissions/{id}/media", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"fileName":"photo.jpg","fileType":"jpeg","fileSizeBytes":1024}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.storageUrl").exists());
    }

    @Test
    @WithMockUser(roles = "CONTRIBUTOR")
    void attachAsset_validBody_returns201() throws Exception {
        UUID submissionId = UUID.randomUUID();
        when(submissionService.attachAsset(any(), any(), any())).thenReturn(responseDto(submissionId, SubmissionStatus.draft));

        mockMvc.perform(post("/api/v1/submissions/{id}/assets", submissionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"mediaAssetId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(submissionId.toString()));
    }

    private static SubmissionResponseDto responseDto(UUID id, SubmissionStatus status) {
        return SubmissionResponseDto.from(submission(id, status), List.of());
    }

    private static SubmissionSummaryDto summaryDto(UUID id) {
        return SubmissionSummaryDto.from(submission(id, SubmissionStatus.draft), 2L);
    }

    private static Submission submission(UUID id, SubmissionStatus status) {
        UUID institutionId = UUID.randomUUID();
        Institution institution = new Institution();
        institution.setId(institutionId);
        institution.setName("CIT-U");
        institution.setCode("CIT-U");
        institution.setEmailDomain("cit.edu.ph");

        User contributor = new User();
        contributor.setId(UUID.randomUUID());
        contributor.setEmail("contributor@cit.edu.ph");
        contributor.setRole(UserRole.contributor);
        contributor.setAccountState(UserStatus.active);
        contributor.setInstitution(institution);

        Submission submission = new Submission();
        submission.setId(id);
        submission.setContributor(contributor);
        submission.setInstitution(institution);
        submission.setEventTitle("Research Expo");
        submission.setEventDate(LocalDate.of(2026, 6, 1));
        submission.setCaption("Caption");
        submission.setDescription("Description");
        submission.setStatus(status);
        submission.setScheduledAt(Instant.parse("2026-06-01T08:00:00Z"));
        return submission;
    }
}
