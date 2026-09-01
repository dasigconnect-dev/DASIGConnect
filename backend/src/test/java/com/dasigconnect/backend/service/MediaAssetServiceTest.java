package com.dasigconnect.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.model.dto.media.MediaAssetBulkDeleteRequestDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetListResponseDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAlbum;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.MediaAlbumRepository;
import com.dasigconnect.backend.repository.MediaAssetEmbeddingRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.security.JwtUserDetails;

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
    private MediaAlbumRepository mediaAlbumRepository;
    @Mock
    private com.dasigconnect.backend.repository.InstitutionRepository institutionRepository;
    @Mock
    private AssetTagRepository assetTagRepository;
    @Mock
    private SubmissionService submissionService;
    @Mock
    private MediaStorageService mediaStorage;
    @Mock
    private AIClassificationService aiClassificationService;
    @Mock
    private com.dasigconnect.backend.external.VoyageAIClient voyageAIClient;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private com.dasigconnect.backend.repository.AuditLogRepository auditLogRepository;
    @Mock
    private com.dasigconnect.backend.repository.UserRepository userRepository;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private MediaAssetService mediaAssetService;

    @BeforeEach
    void setUp() {
        mediaAssetService = new MediaAssetService(
                mediaAssetRepository,
                submissionRepository,
                submissionMediaAssetRepository,
                assetTagRepository,
                mediaAlbumRepository,
                mediaAssetEmbeddingRepository,
                institutionRepository,
                submissionService,
                mediaStorage,
                aiClassificationService,
                voyageAIClient,
                auditLogService,
                auditLogRepository,
                userRepository,
                objectMapper);
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

        mediaAssetService.delete(assetId, false, user(UUID.randomUUID(), "admin", institutionId));

        verify(mediaAssetRepository).save(asset);
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
        // one summary row for the whole operation, not one per asset
        verify(auditLogService).record(any(), eq("MEDIA_BULK_DELETED"), isNull(), isNull(), isNull(), any());
        verify(auditLogService, never()).record(any(), eq("MEDIA_ASSET_DELETED"), any(), any(), any(), any());
    }

    @Test
    void list_hidesAssetAttachedOnlyToDraftSubmissionEvenFromUploader() {
        UUID institutionId = UUID.randomUUID();
        UUID uploaderId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = asset(assetId, institutionId, uploaderId);
        when(mediaAssetRepository.findActiveByInstitutionIds(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of(asset));
        when(submissionMediaAssetRepository.findAssetIdsWithAnySubmissionLink(List.of(assetId)))
                .thenReturn(Set.of(assetId));
        when(submissionMediaAssetRepository.findAssetIdsUsedBeyondDraft(List.of(assetId))).thenReturn(Set.of());

        MediaAssetListResponseDto resultForUploader = mediaAssetService.list(
                null, null, null, null, null, null, null, 1, 20, null,
                user(uploaderId, "contributor", institutionId));
        MediaAssetListResponseDto resultForOtherUser = mediaAssetService.list(
                null, null, null, null, null, null, null, 1, 20, null,
                user(UUID.randomUUID(), "contributor", institutionId));

        assertTrue(resultForUploader.getItems().isEmpty());
        assertTrue(resultForOtherUser.getItems().isEmpty());
    }

    @Test
    void list_showsStandaloneAssetNeverAttachedToAnySubmission() {
        UUID institutionId = UUID.randomUUID();
        UUID uploaderId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = asset(assetId, institutionId, uploaderId);
        when(mediaAssetRepository.findActiveByInstitutionIds(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of(asset));
        when(submissionMediaAssetRepository.findAssetIdsWithAnySubmissionLink(List.of(assetId)))
                .thenReturn(Set.of());

        MediaAssetListResponseDto result = mediaAssetService.list(
                null, null, null, null, null, null, null, 1, 20, null,
                user(UUID.randomUUID(), "contributor", institutionId));

        assertEquals(1, result.getItems().size());
    }

    @Test
    void list_showsAssetUsedBeyondDraftToOtherUsers() {
        UUID institutionId = UUID.randomUUID();
        UUID uploaderId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = asset(assetId, institutionId, uploaderId);
        when(mediaAssetRepository.findActiveByInstitutionIds(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of(asset));
        when(submissionMediaAssetRepository.findAssetIdsWithAnySubmissionLink(List.of(assetId)))
                .thenReturn(Set.of(assetId));
        when(submissionMediaAssetRepository.findAssetIdsUsedBeyondDraft(List.of(assetId))).thenReturn(Set.of(assetId));

        MediaAssetListResponseDto result = mediaAssetService.list(
                null, null, null, null, null, null, null, 1, 20, null,
                user(UUID.randomUUID(), "contributor", institutionId));

        assertEquals(1, result.getItems().size());
    }

    @Test
    void listAlbums_adminWithoutInstitution_returnsAlbumsAcrossInstitutions() {
        MediaAlbum a = album(UUID.randomUUID(), UUID.randomUUID(), null);
        MediaAlbum b = album(UUID.randomUUID(), UUID.randomUUID(), null);
        when(mediaAlbumRepository.findAll()).thenReturn(List.of(a, b));
        when(mediaAlbumRepository.countChildAlbumsByParentAllInstitutions()).thenReturn(List.of());
        when(mediaAssetRepository.countActiveAssetsByAlbumAllInstitutions()).thenReturn(List.of());

        var result = mediaAssetService.listAlbums(null, user(UUID.randomUUID(), "admin", null));

        assertEquals(2, result.size());
    }

    @Test
    void deleteAlbum_contributorCannotDeleteAnotherInstitutionFolder() {
        UUID ownInstitution = UUID.randomUUID();
        UUID otherInstitution = UUID.randomUUID();
        UUID albumId = UUID.randomUUID();
        when(mediaAlbumRepository.findById(albumId)).thenReturn(Optional.of(album(albumId, otherInstitution, null)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> mediaAssetService.deleteAlbum(albumId, user(UUID.randomUUID(), "contributor", ownInstitution)));
        assertEquals(403, ex.getStatusCode().value());
        verify(mediaAlbumRepository, never()).delete(any());
    }

    @Test
    void deleteAlbum_adminCanDeleteAnyInstitutionFolder() {
        UUID albumId = UUID.randomUUID();
        MediaAlbum album = album(albumId, UUID.randomUUID(), null);
        when(mediaAlbumRepository.findById(albumId)).thenReturn(Optional.of(album));
        when(mediaAlbumRepository.countByParentAlbumId(albumId)).thenReturn(0L);
        when(mediaAssetRepository.countByMediaAlbumIdAndDeletedAtIsNull(albumId)).thenReturn(0L);

        mediaAssetService.deleteAlbum(albumId, user(UUID.randomUUID(), "admin", null));

        verify(mediaAlbumRepository).delete(album);
    }

    @Test
    void moveAlbum_intoOwnDescendant_isRejected() {
        UUID institutionId = UUID.randomUUID();
        UUID albumId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        when(mediaAlbumRepository.findById(albumId)).thenReturn(Optional.of(album(albumId, institutionId, null)));
        when(mediaAlbumRepository.findById(childId)).thenReturn(Optional.of(album(childId, institutionId, null)));
        when(mediaAlbumRepository.findDescendantIds(albumId)).thenReturn(List.of(childId));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> mediaAssetService.moveAlbum(albumId, childId, null, user(UUID.randomUUID(), "contributor", institutionId)));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void moveAlbum_contributorCannotMoveIntoUnrelatedInstitution() {
        UUID ownInstitution = UUID.randomUUID();
        UUID otherInstitution = UUID.randomUUID();
        UUID albumId = UUID.randomUUID();
        UUID destinationParentId = UUID.randomUUID();
        when(mediaAlbumRepository.findById(albumId)).thenReturn(Optional.of(album(albumId, ownInstitution, null)));
        when(mediaAlbumRepository.findById(destinationParentId))
                .thenReturn(Optional.of(album(destinationParentId, otherInstitution, null)));
        when(institutionRepository.findFirstByIsProtectedTrueOrderByCreatedAtAsc()).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> mediaAssetService.moveAlbum(albumId, destinationParentId, null,
                        user(UUID.randomUUID(), "contributor", ownInstitution)));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void moveAlbum_intoItself_isRejected() {
        UUID institutionId = UUID.randomUUID();
        UUID albumId = UUID.randomUUID();
        when(mediaAlbumRepository.findById(albumId)).thenReturn(Optional.of(album(albumId, institutionId, null)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> mediaAssetService.moveAlbum(albumId, albumId, null, user(UUID.randomUUID(), "contributor", institutionId)));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void deleteAlbum_withSubAlbums_isRejected() {
        UUID institutionId = UUID.randomUUID();
        UUID albumId = UUID.randomUUID();
        when(mediaAlbumRepository.findById(albumId)).thenReturn(Optional.of(album(albumId, institutionId, null)));
        when(mediaAlbumRepository.countByParentAlbumId(albumId)).thenReturn(2L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> mediaAssetService.deleteAlbum(albumId, user(UUID.randomUUID(), "admin", null)));
        assertEquals(409, ex.getStatusCode().value());
        verify(mediaAlbumRepository, never()).delete(any());
    }

    @Test
    void deleteAlbum_withAssets_isRejected() {
        UUID institutionId = UUID.randomUUID();
        UUID albumId = UUID.randomUUID();
        when(mediaAlbumRepository.findById(albumId)).thenReturn(Optional.of(album(albumId, institutionId, null)));
        when(mediaAlbumRepository.countByParentAlbumId(albumId)).thenReturn(0L);
        when(mediaAssetRepository.countByMediaAlbumIdAndDeletedAtIsNull(albumId)).thenReturn(5L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> mediaAssetService.deleteAlbum(albumId, user(UUID.randomUUID(), "admin", null)));
        assertEquals(409, ex.getStatusCode().value());
        verify(mediaAlbumRepository, never()).delete(any());
    }

    @Test
    void deleteAlbum_whenEmpty_deletes() {
        UUID institutionId = UUID.randomUUID();
        UUID albumId = UUID.randomUUID();
        MediaAlbum album = album(albumId, institutionId, null);
        when(mediaAlbumRepository.findById(albumId)).thenReturn(Optional.of(album));
        when(mediaAlbumRepository.countByParentAlbumId(albumId)).thenReturn(0L);
        when(mediaAssetRepository.countByMediaAlbumIdAndDeletedAtIsNull(albumId)).thenReturn(0L);

        mediaAssetService.deleteAlbum(albumId, user(UUID.randomUUID(), "admin", null));

        verify(mediaAlbumRepository).delete(album);
    }

    @Test
    void deleteAlbum_contributorCanDeleteFolderTheyCreated() {
        UUID institutionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID albumId = UUID.randomUUID();
        MediaAlbum album = album(albumId, institutionId, null);
        album.setCreatedBy(userId);
        when(mediaAlbumRepository.findById(albumId)).thenReturn(Optional.of(album));
        when(mediaAlbumRepository.countByParentAlbumId(albumId)).thenReturn(0L);
        when(mediaAssetRepository.countByMediaAlbumIdAndDeletedAtIsNull(albumId)).thenReturn(0L);

        mediaAssetService.deleteAlbum(albumId, user(userId, "contributor", institutionId));

        verify(mediaAlbumRepository).delete(album);
    }

    @Test
    void deleteAlbum_contributorCannotDeleteFolderCreatedByAnother() {
        UUID institutionId = UUID.randomUUID();
        UUID albumId = UUID.randomUUID();
        MediaAlbum album = album(albumId, institutionId, null);
        album.setCreatedBy(UUID.randomUUID());
        when(mediaAlbumRepository.findById(albumId)).thenReturn(Optional.of(album));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> mediaAssetService.deleteAlbum(albumId, user(UUID.randomUUID(), "contributor", institutionId)));
        assertEquals(403, ex.getStatusCode().value());
        verify(mediaAlbumRepository, never()).delete(any());
    }

    @Test
    void updateAlbum_recordsMovedAuditEntry() {
        UUID institutionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID targetAlbumId = UUID.randomUUID();
        MediaAsset asset = asset(assetId, institutionId, UUID.randomUUID());
        asset.setMediaAlbum(album(UUID.randomUUID(), institutionId, null));
        MediaAlbum target = album(targetAlbumId, institutionId, null);

        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset));
        when(mediaAlbumRepository.findById(targetAlbumId)).thenReturn(Optional.of(target));
        when(mediaAssetRepository.save(asset)).thenReturn(asset);
        when(assetTagRepository.findByMediaAssetIdOrderByCreatedAtAsc(assetId)).thenReturn(List.of());

        com.dasigconnect.backend.model.dto.media.MediaAssetAlbumRequestDto dto =
                new com.dasigconnect.backend.model.dto.media.MediaAssetAlbumRequestDto();
        dto.setAlbumId(targetAlbumId);

        mediaAssetService.updateAlbum(assetId, dto, user(UUID.randomUUID(), "moderator", institutionId));

        verify(auditLogService).record(any(), eq("MEDIA_ASSET_MOVED"), isNull(), isNull(), eq(assetId), any());
    }

    @Test
    void delete_recordsDeletedAuditEntry() {
        UUID institutionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = asset(assetId, institutionId, userId);
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset));

        mediaAssetService.delete(assetId, false, user(userId, "contributor", institutionId));

        verify(auditLogService).record(any(), eq("MEDIA_ASSET_DELETED"), isNull(), isNull(), eq(assetId), any());
    }

    @Test
    void history_returnsSynthesizedUploadPlusAuditEntriesNewestFirst() {
        UUID institutionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        MediaAsset asset = asset(assetId, institutionId, actorId);
        ReflectionTestUtils.setField(asset, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
        asset.getUploader().setEmail("owner@example.edu");

        com.dasigconnect.backend.model.entity.AuditLog row = new com.dasigconnect.backend.model.entity.AuditLog();
        row.setAction("MEDIA_ASSET_MOVED");
        row.setResourceId(assetId);
        row.setMetadata("{\"toAlbumName\":\"Campaigns\"}");
        User actor = asset.getUploader();
        row.setActor(actor);
        ReflectionTestUtils.setField(row, "createdAt", Instant.parse("2026-02-01T00:00:00Z"));

        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset));
        when(auditLogRepository.findByResourceIdOrderByCreatedAtDesc(assetId)).thenReturn(List.of(row));
        when(userRepository.findAllById(any())).thenReturn(List.of(actor));

        var history = mediaAssetService.history(assetId, user(UUID.randomUUID(), "moderator", institutionId));

        assertEquals(2, history.size());
        assertEquals("Moved to Campaigns", history.get(0).summary());
        assertEquals("MEDIA_ASSET_UPLOADED", history.get(1).action());
    }

    @Test
    void history_rejectsAssetFromAnotherInstitution() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = asset(assetId, UUID.randomUUID(), UUID.randomUUID());
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> mediaAssetService.history(assetId, user(UUID.randomUUID(), "contributor", UUID.randomUUID())));
        assertEquals(404, ex.getStatusCode().value());
    }

    private static MediaAlbum album(UUID id, UUID institutionId, MediaAlbum parent) {
        MediaAlbum album = new MediaAlbum();
        album.setId(id);
        album.setName("Album " + id.toString().substring(0, 4));
        album.setInstitution(institution(institutionId));
        album.setParentAlbum(parent);
        return album;
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
}
