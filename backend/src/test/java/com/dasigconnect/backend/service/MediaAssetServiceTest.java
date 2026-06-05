package com.dasigconnect.backend.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.model.dto.media.MediaAssetBulkDeleteRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetCurationEditDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetUploadRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetUploadUrlRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaBatchCurationRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaImportBatchCreateRequestDto;
import com.dasigconnect.backend.model.entity.AuditLog;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.MediaImportBatch;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.AuditLogRepository;
import com.dasigconnect.backend.repository.MediaAssetEmbeddingRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.MediaImportBatchRepository;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class MediaAssetServiceTest {

    @Mock
    private MediaAssetRepository mediaAssetRepository;
    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private SubmissionMediaAssetRepository submissionMediaAssetRepository;
    @Mock
    private MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository;
    @Mock
    private MediaImportBatchRepository mediaImportBatchRepository;
    @Mock
    private AssetTagRepository assetTagRepository;
    @Mock
    private SubmissionService submissionService;
    @Mock
    private SupabaseStorageService supabaseStorageService;
    @Mock
    private MediaIngestionQueueService mediaIngestionQueueService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private EntityManager entityManager;

    private MediaAssetService mediaAssetService;

    @BeforeEach
    void setUp() {
        mediaAssetService = new MediaAssetService(
                mediaAssetRepository,
                submissionRepository,
                submissionMediaAssetRepository,
                assetTagRepository,
                mediaAssetEmbeddingRepository,
                mediaImportBatchRepository,
                submissionService,
                supabaseStorageService,
                mediaIngestionQueueService,
                userRepository,
                auditLogService,
                auditLogRepository);
        ReflectionTestUtils.setField(mediaAssetService, "entityManager", entityManager);
    }

    @Test
    void delete_contributorCanDeleteOwnAsset() {
        UUID institutionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = asset(assetId, institutionId, userId);
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset));

        mediaAssetService.delete(assetId, false, user(userId, "contributor", institutionId));

        verify(mediaAssetRepository).save(asset);
        org.junit.jupiter.api.Assertions.assertEquals(userId, asset.getDeletedByUserId());
        // UC-4.11: deletion is audited.
        verify(auditLogService).record(any(), org.mockito.ArgumentMatchers.eq("MEDIA_ASSET_DELETED"),
                any(), any(), org.mockito.ArgumentMatchers.eq(assetId), any());
    }

    @Test
    void delete_contributorCannotDeleteOtherContributorAsset() {
        UUID institutionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = asset(assetId, institutionId, UUID.randomUUID());
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset));

        assertThrows(ResponseStatusException.class,
                () -> mediaAssetService.delete(assetId, false, user(UUID.randomUUID(), "contributor", institutionId)));

        verify(mediaAssetRepository, never()).save(any());
    }

    @Test
    void delete_validatorCanDeleteInstitutionAsset() {
        UUID institutionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = asset(assetId, institutionId, UUID.randomUUID());
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset));

        mediaAssetService.delete(assetId, false, user(UUID.randomUUID(), "validator", institutionId));

        verify(mediaAssetRepository).save(asset);
    }

    @Test
    void getHistory_returnsAuditTrailForViewableAsset() {
        UUID institutionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = asset(assetId, institutionId, UUID.randomUUID());
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset));

        AuditLog log = new AuditLog();
        log.setAction("MEDIA_ASSET_UPLOADED");
        log.setResourceId(assetId);
        when(auditLogRepository.findByResourceIdWithActor(assetId)).thenReturn(List.of(log));

        var history = mediaAssetService.getHistory(assetId, user(UUID.randomUUID(), "validator", institutionId));

        org.junit.jupiter.api.Assertions.assertEquals(1, history.size());
        org.junit.jupiter.api.Assertions.assertEquals("MEDIA_ASSET_UPLOADED", history.get(0).action());
    }

    @Test
    void bulkDelete_adminDeletesMultipleInstitutionAssets() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        MediaAsset first = asset(firstId, UUID.randomUUID(), UUID.randomUUID());
        MediaAsset second = asset(secondId, UUID.randomUUID(), UUID.randomUUID());
        when(mediaAssetRepository.findActiveById(firstId)).thenReturn(Optional.of(first));
        when(mediaAssetRepository.findActiveById(secondId)).thenReturn(Optional.of(second));
        MediaAssetBulkDeleteRequestDto dto = new MediaAssetBulkDeleteRequestDto();
        dto.setAssetIds(List.of(firstId, secondId));

        mediaAssetService.bulkDelete(dto, user(UUID.randomUUID(), "admin", null));

        verify(mediaAssetRepository).saveAll(List.of(first, second));
    }

    @Test
    void createUploadUrl_adminWithoutInstitution_returns400() {
        MediaAssetUploadUrlRequestDto dto = new MediaAssetUploadUrlRequestDto();
        dto.setFileName("photo.jpg");
        dto.setFileType("jpeg");

        assertThrows(ResponseStatusException.class,
                () -> mediaAssetService.createUploadUrl(dto, user(UUID.randomUUID(), "admin", null)));

        verify(supabaseStorageService, never()).createSignedUploadUrl(any());
    }

    @Test
    void createUploadUrl_adminWithInstitution_usesSelectedInstitutionPath() {
        UUID institutionId = UUID.randomUUID();
        MediaAssetUploadUrlRequestDto dto = new MediaAssetUploadUrlRequestDto();
        dto.setFileName("photo.jpg");
        dto.setFileType("jpeg");
        dto.setInstitutionId(institutionId);
        when(supabaseStorageService.createSignedUploadUrl(any())).thenReturn("signed");
        when(supabaseStorageService.getPublicUrl(any())).thenReturn("public");

        var response = mediaAssetService.createUploadUrl(dto, user(UUID.randomUUID(), "admin", null));

        assertTrue(response.getPath().startsWith(institutionId + "/"));
        verify(supabaseStorageService).createSignedUploadUrl(any());
    }

    @Test
    void createImportBatch_adminWithoutInstitution_returns400() {
        MediaImportBatchCreateRequestDto dto = new MediaImportBatchCreateRequestDto();
        dto.setAssetCount(80);

        assertThrows(ResponseStatusException.class,
                () -> mediaAssetService.createImportBatch(dto, user(UUID.randomUUID(), "admin", null)));

        verify(mediaImportBatchRepository, never()).save(any());
    }

    @Test
    void createImportBatch_adminWithInstitution_savesBatch() {
        UUID institutionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Institution institution = institution(institutionId);
        User uploader = uploader(userId);
        MediaImportBatchCreateRequestDto dto = new MediaImportBatchCreateRequestDto();
        dto.setAssetCount(80);
        dto.setInstitutionId(institutionId);
        when(entityManager.getReference(Institution.class, institutionId)).thenReturn(institution);
        when(entityManager.getReference(User.class, userId)).thenReturn(uploader);
        when(mediaImportBatchRepository.save(any())).thenAnswer(invocation -> {
            MediaImportBatch batch = invocation.getArgument(0);
            batch.setId(UUID.randomUUID());
            return batch;
        });

        mediaAssetService.createImportBatch(dto, user(userId, "admin", null));

        ArgumentCaptor<MediaImportBatch> captor = ArgumentCaptor.forClass(MediaImportBatch.class);
        verify(mediaImportBatchRepository).save(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(institutionId, captor.getValue().getInstitution().getId());
        org.junit.jupiter.api.Assertions.assertEquals(userId, captor.getValue().getUploadedBy().getId());
        org.junit.jupiter.api.Assertions.assertEquals(80, captor.getValue().getAssetCount());
    }

    @Test
    void listImportBatches_returnsRecentBatchesWithCounts() {
        UUID institutionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        MediaImportBatch batch = importBatch(batchId, institutionId, userId, 3);
        MediaAsset readyCurated = asset(UUID.randomUUID(), institutionId, userId);
        readyCurated.setStatus(com.dasigconnect.backend.model.entity.MediaAssetStatus.READY);
        readyCurated.setCuratedAt(java.time.Instant.now());
        MediaAsset processing = asset(UUID.randomUUID(), institutionId, userId);
        processing.setStatus(com.dasigconnect.backend.model.entity.MediaAssetStatus.PROCESSING);
        when(mediaImportBatchRepository.findByInstitution(institutionId)).thenReturn(List.of(batch));
        when(mediaAssetRepository.findActiveByImportBatch(batchId, institutionId)).thenReturn(List.of(readyCurated, processing));

        var response = mediaAssetService.listImportBatches(null, user(userId, "contributor", institutionId));

        org.junit.jupiter.api.Assertions.assertEquals(1, response.size());
        org.junit.jupiter.api.Assertions.assertEquals(2, response.get(0).getRegisteredAssetCount());
        org.junit.jupiter.api.Assertions.assertEquals(1, response.get(0).getReadyAssetCount());
        org.junit.jupiter.api.Assertions.assertEquals(1, response.get(0).getCuratedAssetCount());
    }

    @Test
    void upload_withImportBatch_setsImportBatchId() {
        UUID institutionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        MediaImportBatch batch = importBatch(batchId, institutionId, userId, 2);
        MediaAssetUploadRequestDto dto = uploadRequest();
        dto.setImportBatchId(batchId);
        when(mediaImportBatchRepository.findByIdAndInstitution(batchId, institutionId)).thenReturn(Optional.of(batch));
        when(entityManager.getReference(Institution.class, institutionId)).thenReturn(institution(institutionId));
        when(entityManager.getReference(User.class, userId)).thenReturn(uploader(userId));
        when(mediaAssetRepository.existsByAssetCode(any())).thenReturn(false);
        when(mediaAssetRepository.save(any())).thenAnswer(invocation -> {
            MediaAsset asset = invocation.getArgument(0);
            asset.setId(UUID.randomUUID());
            return asset;
        });

        mediaAssetService.upload(dto, user(userId, "contributor", institutionId));

        ArgumentCaptor<MediaAsset> captor = ArgumentCaptor.forClass(MediaAsset.class);
        verify(mediaAssetRepository).save(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(batchId, captor.getValue().getImportBatchId());
        verify(mediaIngestionQueueService).enqueue(any(), eq("https://storage.example/photo.jpg"));
    }

    @Test
    void upload_withImportBatchFromOtherInstitution_returns400() {
        UUID institutionId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        MediaAssetUploadRequestDto dto = uploadRequest();
        dto.setImportBatchId(batchId);
        when(mediaImportBatchRepository.findByIdAndInstitution(batchId, institutionId)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> mediaAssetService.upload(dto, user(UUID.randomUUID(), "contributor", institutionId)));

        verify(mediaAssetRepository, never()).save(any());
    }

    @Test
    void listImportBatchAssets_validBatch_returnsDetails() {
        UUID institutionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        MediaImportBatch batch = importBatch(batchId, institutionId, userId, 1);
        MediaAsset asset = asset(UUID.randomUUID(), institutionId, userId);
        asset.setImportBatchId(batchId);
        when(mediaImportBatchRepository.findByIdAndInstitution(batchId, institutionId)).thenReturn(Optional.of(batch));
        when(mediaAssetRepository.findActiveByImportBatch(batchId, institutionId)).thenReturn(List.of(asset));
        when(assetTagRepository.findByMediaAssetIdOrderByCreatedAtAsc(asset.getId())).thenReturn(List.of());

        var response = mediaAssetService.listImportBatchAssets(batchId, null, user(userId, "contributor", institutionId));

        org.junit.jupiter.api.Assertions.assertEquals(1, response.size());
        org.junit.jupiter.api.Assertions.assertEquals(asset.getId(), response.get(0).getId());
    }

    @Test
    void markImportBatchCurated_setsCuratedAtAndTitleFallback() {
        UUID institutionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        MediaImportBatch batch = importBatch(batchId, institutionId, userId, 1);
        MediaAsset asset = asset(UUID.randomUUID(), institutionId, userId);
        asset.setFileName("event-photo.jpg");
        asset.setTitle(null);
        when(mediaImportBatchRepository.findByIdAndInstitution(batchId, institutionId)).thenReturn(Optional.of(batch));
        when(mediaAssetRepository.findActiveByImportBatch(batchId, institutionId)).thenReturn(List.of(asset));

        var response = mediaAssetService.markImportBatchCurated(batchId, null, user(userId, "contributor", institutionId));

        org.junit.jupiter.api.Assertions.assertEquals(1, response.getCuratedCount());
        org.junit.jupiter.api.Assertions.assertEquals("event photo", asset.getTitle());
        org.junit.jupiter.api.Assertions.assertNotNull(asset.getCuratedAt());
        verify(mediaAssetRepository).saveAll(List.of(asset));
    }

    @Test
    void markImportBatchCurated_appliesTitleAndManualTagEdits() {
        UUID institutionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        MediaImportBatch batch = importBatch(batchId, institutionId, userId, 1);
        MediaAsset asset = asset(assetId, institutionId, userId);
        asset.setFileName("event-photo.jpg");
        when(mediaImportBatchRepository.findByIdAndInstitution(batchId, institutionId)).thenReturn(Optional.of(batch));
        when(mediaAssetRepository.findActiveByImportBatch(batchId, institutionId)).thenReturn(List.of(asset));

        MediaAssetCurationEditDto edit = new MediaAssetCurationEditDto();
        edit.setAssetId(assetId);
        edit.setTitle("  Research awarding  ");
        edit.setTags(List.of(" students ", "award", "students", ""));
        MediaBatchCurationRequestDto request = new MediaBatchCurationRequestDto();
        request.setEdits(List.of(edit));

        mediaAssetService.markImportBatchCurated(batchId, null, request, user(userId, "contributor", institutionId));

        org.junit.jupiter.api.Assertions.assertEquals("Research awarding", asset.getTitle());
        verify(assetTagRepository).deleteByMediaAssetId(assetId);
        verify(assetTagRepository, org.mockito.Mockito.times(2)).save(any());
        verify(mediaAssetRepository).saveAll(List.of(asset));
    }

    @Test
    void markImportBatchCurated_editOutsideBatchReturns400() {
        UUID institutionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        MediaImportBatch batch = importBatch(batchId, institutionId, userId, 1);
        MediaAsset asset = asset(UUID.randomUUID(), institutionId, userId);
        when(mediaImportBatchRepository.findByIdAndInstitution(batchId, institutionId)).thenReturn(Optional.of(batch));
        when(mediaAssetRepository.findActiveByImportBatch(batchId, institutionId)).thenReturn(List.of(asset));

        MediaAssetCurationEditDto edit = new MediaAssetCurationEditDto();
        edit.setAssetId(UUID.randomUUID());
        edit.setTitle("Outside asset");
        MediaBatchCurationRequestDto request = new MediaBatchCurationRequestDto();
        request.setEdits(List.of(edit));

        assertThrows(ResponseStatusException.class,
                () -> mediaAssetService.markImportBatchCurated(batchId, null, request, user(userId, "contributor", institutionId)));

        verify(mediaAssetRepository, never()).saveAll(any());
    }

    private static JwtUserDetails user(UUID userId, String role, UUID institutionId) {
        return new JwtUserDetails(userId, role + "@example.edu", role, institutionId);
    }

    private static MediaAsset asset(UUID assetId, UUID institutionId, UUID uploaderId) {
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        asset.setAssetCode("ASSET-" + assetId.toString().substring(0, 8));
        asset.setStorageUrl("https://storage.example/asset.jpg");
        asset.setFileName("asset.jpg");
        asset.setFileType(MediaFileType.jpeg);
        asset.setFileSizeBytes(1024);
        asset.setInstitution(institution(institutionId));
        asset.setUploader(uploader(uploaderId));
        return asset;
    }

    private static Institution institution(UUID id) {
        Institution institution = new Institution();
        institution.setId(id);
        institution.setName("Institution");
        institution.setCode("INST");
        return institution;
    }

    private static User uploader(UUID id) {
        User user = new User();
        user.setId(id);
        user.setEmail("uploader@example.edu");
        return user;
    }

    private static MediaAssetUploadRequestDto uploadRequest() {
        MediaAssetUploadRequestDto dto = new MediaAssetUploadRequestDto();
        dto.setStorageUrl("https://storage.example/photo.jpg");
        dto.setFileName("photo.jpg");
        dto.setFileType("jpeg");
        dto.setFileSizeBytes(2048L);
        return dto;
    }

    private static MediaImportBatch importBatch(UUID id, UUID institutionId, UUID uploadedBy, int assetCount) {
        MediaImportBatch batch = new MediaImportBatch();
        batch.setId(id);
        batch.setInstitution(institution(institutionId));
        batch.setUploadedBy(uploader(uploadedBy));
        batch.setAssetCount(assetCount);
        return batch;
    }
}
