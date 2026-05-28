package com.dasigconnect.backend.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.model.dto.media.MediaAssetBulkDeleteRequestDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.AssetTagRepository;
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
    private AssetTagRepository assetTagRepository;
    @Mock
    private SubmissionService submissionService;
    @Mock
    private SupabaseStorageService supabaseStorageService;
    @Mock
    private AIClassificationService aiClassificationService;

    private MediaAssetService mediaAssetService;

    @BeforeEach
    void setUp() {
        mediaAssetService = new MediaAssetService(
                mediaAssetRepository,
                submissionRepository,
                submissionMediaAssetRepository,
                assetTagRepository,
                submissionService,
                supabaseStorageService,
                aiClassificationService);
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

        mediaAssetService.delete(assetId, false, user(UUID.randomUUID(), "validator", institutionId));

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
