package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.dto.media.TrashItemDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetStatus;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MediaTrashServiceTest {

    @Mock private MediaAssetRepository mediaAssetRepository;
    @Mock private MediaAssetRetentionService retentionService;
    @Mock private AuditLogService auditLogService;
    @Mock private UserRepository userRepository;

    private MediaTrashService service;

    @BeforeEach
    void setUp() {
        service = new MediaTrashService(mediaAssetRepository, retentionService, auditLogService, userRepository);
    }

    @Test
    void listTrash_contributor_returnsOwnUploadsWithDaysLeft() {
        UUID institutionId = UUID.randomUUID();
        UUID uploaderId = UUID.randomUUID();
        MediaAsset asset = asset(UUID.randomUUID(), institutionId, uploaderId, Instant.now().minusSeconds(5 * 86_400));
        when(mediaAssetRepository.findTrashByUploader(uploaderId)).thenReturn(List.of(asset));
        when(retentionService.getRetentionDays()).thenReturn(30);

        List<TrashItemDto> trash = service.listTrash(null, user(uploaderId, "contributor", institutionId));

        assertThat(trash).hasSize(1);
        // deleted 5 days ago, 30-day window => ~25 days left
        assertThat(trash.get(0).daysUntilPurge()).isBetween(24L, 25L);
        verify(mediaAssetRepository, never()).findTrash(any());
    }

    @Test
    void listTrash_admin_withoutInstitution_isNetworkWide() {
        MediaAsset asset = asset(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        when(mediaAssetRepository.findTrash(isNull())).thenReturn(List.of(asset));
        when(retentionService.getRetentionDays()).thenReturn(30);

        List<TrashItemDto> trash = service.listTrash(null, admin());

        assertThat(trash).hasSize(1);
    }

    @Test
    void restore_clearsDeletionAndAudits() {
        UUID institutionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = asset(assetId, institutionId, UUID.randomUUID(), Instant.now());
        when(mediaAssetRepository.findTrashedById(assetId)).thenReturn(Optional.of(asset));

        service.restore(assetId, user(UUID.randomUUID(), "validator", institutionId));

        assertThat(asset.getDeletedAt()).isNull();
        assertThat(asset.getStatus()).isEqualTo(MediaAssetStatus.READY);
        verify(mediaAssetRepository).save(asset);
        verify(auditLogService).record(any(), eq("MEDIA_ASSET_RESTORED"), any(), any(), eq(assetId), any());
    }

    @Test
    void purgeNow_purgesDataAndAudits() {
        UUID institutionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = asset(assetId, institutionId, UUID.randomUUID(), Instant.now());
        when(mediaAssetRepository.findTrashedById(assetId)).thenReturn(Optional.of(asset));
        when(retentionService.purgeAssetData(asset)).thenReturn(true);

        service.purgeNow(assetId, user(UUID.randomUUID(), "validator", institutionId));

        verify(retentionService).purgeAssetData(asset);
        verify(auditLogService).record(any(), eq("MEDIA_ASSET_PURGED"), any(), any(), eq(assetId), any());
    }

    @Test
    void restore_crossTenant_isHidden() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = asset(assetId, UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        when(mediaAssetRepository.findTrashedById(assetId)).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> service.restore(assetId, user(UUID.randomUUID(), "validator", UUID.randomUUID())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        verify(mediaAssetRepository, never()).save(any());
    }

    @Test
    void purgeNow_contributorNotOwner_isForbidden() {
        UUID institutionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = asset(assetId, institutionId, UUID.randomUUID(), Instant.now());
        when(mediaAssetRepository.findTrashedById(assetId)).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> service.purgeNow(assetId, user(UUID.randomUUID(), "contributor", institutionId)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        verify(retentionService, never()).purgeAssetData(any());
    }

    private static MediaAsset asset(UUID id, UUID institutionId, UUID uploaderId, Instant deletedAt) {
        MediaAsset asset = new MediaAsset();
        asset.setId(id);
        asset.setAssetCode("ASSET-" + id.toString().substring(0, 8));
        asset.setStorageUrl("https://storage.example/" + id + ".jpg");
        asset.setFileName(id + ".jpg");
        asset.setFileType(MediaFileType.jpeg);
        asset.setFileSizeBytes(2048);
        asset.setDeletedAt(deletedAt);
        asset.setStatus(MediaAssetStatus.DELETED);
        Institution institution = new Institution();
        institution.setId(institutionId);
        institution.setName("Institution");
        institution.setCode("INST");
        asset.setInstitution(institution);
        User uploader = new User();
        uploader.setId(uploaderId);
        uploader.setEmail("uploader@example.edu");
        asset.setUploader(uploader);
        return asset;
    }

    private static JwtUserDetails admin() {
        return new JwtUserDetails(UUID.randomUUID(), "admin@example.edu", "administrator", null);
    }

    private static JwtUserDetails user(UUID userId, String role, UUID institutionId) {
        return new JwtUserDetails(userId, role + "@example.edu", role, institutionId);
    }
}
