package com.dasigconnect.backend.controller;

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

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.model.dto.media.AssetTagDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetDetailDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetListResponseDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetSummaryDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionResponseDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.MediaAssetService;
import com.dasigconnect.backend.service.TenantScopeService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MediaAssetController.class)
@Import(SecurityConfig.class)
class MediaAssetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MediaAssetService mediaAssetService;

    @MockitoBean
    private JWTService jwtService;

    @MockitoBean
    private TenantScopeService tenantScopeService;

    @Test
    void list_withoutAuth_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/media-assets"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void list_authenticated_returnsPagedAssets() throws Exception {
        MediaAsset asset = mediaAsset(UUID.randomUUID());
        MediaAssetListResponseDto response = new MediaAssetListResponseDto(
                List.of(MediaAssetSummaryDto.from(asset)),
                1,
                1,
                25);
        when(mediaAssetService.list(any(), any(), any(), any(), any(), any(), any(Integer.class), any(Integer.class), any(), any()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/media-assets").param("page", "1").param("pageSize", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(asset.getId().toString()))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(25));
    }

    @Test
    @WithMockUser
    void list_withParams_delegatesToService() throws Exception {
        UUID uploaderId = UUID.randomUUID();
        MediaAssetListResponseDto response = new MediaAssetListResponseDto(List.of(), 0, 2, 10);
        when(mediaAssetService.list(any(), any(), any(), any(), any(), any(), any(Integer.class), any(Integer.class), any(), any()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/media-assets")
                .param("query", "award")
                .param("aiCategory", "Awarding")
                .param("uploaderId", uploaderId.toString())
                .param("sort", "name")
                .param("page", "2")
                .param("pageSize", "10"))
                .andExpect(status().isOk());

        verify(mediaAssetService).list(
                eq("award"),
                eq("Awarding"),
                any(),
                eq(uploaderId),
                any(),
                eq("name"),
                eq(2),
                eq(10),
                any(),
                any());
    }

    @Test
    @WithMockUser(roles = "CONTRIBUTOR")
    void useInNewPost_asContributor_returnsSubmission() throws Exception {
        UUID assetId = UUID.randomUUID();
        SubmissionResponseDto response = submissionResponse(UUID.randomUUID());
        when(mediaAssetService.useInNewPost(eq(assetId), any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/media-assets/{id}/use-in-new-post", assetId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"eventTitle":"Research Expo","eventDate":"2026-06-01"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(response.getId().toString()));
    }

    @Test
    @WithMockUser(roles = "CONTRIBUTOR")
    void addToDraft_asContributor_returnsSubmission() throws Exception {
        UUID assetId = UUID.randomUUID();
        SubmissionResponseDto response = submissionResponse(UUID.randomUUID());
        when(mediaAssetService.addToDraft(eq(assetId), any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/media-assets/{id}/add-to-draft", assetId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"submissionId":"%s"}
                        """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(response.getId().toString()));
    }

    @Test
    @WithMockUser
    void upload_authenticated_returns201() throws Exception {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = mediaAsset(assetId);
        asset.setUploader(user(UUID.randomUUID()));
        MediaAssetDetailDto detail = MediaAssetDetailDto.from(asset, List.of(), List.of());
        when(mediaAssetService.upload(any(), any())).thenReturn(detail);

        mockMvc.perform(post("/api/v1/media-assets/upload")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"storageUrl":"https://storage.example/photo.jpg","fileName":"photo.jpg","fileType":"jpeg","fileSizeBytes":2048}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(assetId.toString()));
    }

    @Test
    @WithMockUser
    void addTag_authenticated_returns201() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        AssetTagDto tagDto = new AssetTagDto(tagId, "award", "manual", Instant.now());
        when(mediaAssetService.addTag(eq(assetId), any(), any())).thenReturn(tagDto);

        mockMvc.perform(post("/api/v1/media-assets/{id}/tags", assetId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"label":"award"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(tagId.toString()))
                .andExpect(jsonPath("$.label").value("award"));
    }

    @Test
    @WithMockUser
    void removeTag_authenticated_returns204() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/media-assets/{id}/tags/{tagId}", assetId, tagId))
                .andExpect(status().isNoContent());

        verify(mediaAssetService).removeTag(eq(assetId), eq(tagId), any());
    }

    @Test
    @WithMockUser
    void delete_authenticated_returns204() throws Exception {
        UUID assetId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/media-assets/{id}", assetId).param("force", "true"))
                .andExpect(status().isNoContent());

        verify(mediaAssetService).delete(eq(assetId), eq(true), any());
    }

    @Test
    @WithMockUser
    void bulkDelete_authenticated_returnsDeletedCount() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(mediaAssetService.bulkDelete(any(), any()))
                .thenReturn(new com.dasigconnect.backend.model.dto.media.MediaAssetBulkDeleteResponseDto(List.of(assetId)));

        mockMvc.perform(post("/api/v1/media-assets/bulk-delete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"assetIds":["%s"],"force":true}
                        """.formatted(assetId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedCount").value(1))
                .andExpect(jsonPath("$.deletedIds[0]").value(assetId.toString()));
    }

    private static MediaAsset mediaAsset(UUID id) {
        MediaAsset asset = new MediaAsset();
        asset.setId(id);
        asset.setAssetCode("ASSET-1234");
        asset.setStorageUrl("https://storage.example/asset.jpg");
        asset.setFileName("asset.jpg");
        asset.setFileType(MediaFileType.jpeg);
        asset.setFileSizeBytes(1024L);
        asset.setAiCategory("Awarding");
        asset.setInstitution(institution(UUID.randomUUID()));
        asset.setUploader(user(UUID.randomUUID()));
        return asset;
    }

    private static SubmissionResponseDto submissionResponse(UUID submissionId) {
        Submission submission = new Submission();
        submission.setId(submissionId);
        submission.setStatus(SubmissionStatus.draft);
        submission.setEventTitle("Research Expo");
        submission.setEventDate(LocalDate.of(2026, 6, 1));
        submission.setCaption("Caption");
        submission.setDescription("Description");
        submission.setContributor(user(UUID.randomUUID()));
        submission.setInstitution(institution(UUID.randomUUID()));
        submission.setSubmittedAt(Instant.now());
        return SubmissionResponseDto.from(submission, List.of());
    }

    private static Institution institution(UUID id) {
        Institution institution = new Institution();
        institution.setId(id);
        institution.setName("CIT-U");
        institution.setCode("CIT-U");
        institution.setEmailDomain("cit.edu.ph");
        return institution;
    }

    private static User user(UUID id) {
        User user = new User();
        user.setId(id);
        user.setEmail("contributor@cit.edu.ph");
        return user;
    }
}
