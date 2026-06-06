package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.dto.media.MediaIntegrityResultDto;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaIntegrityStatus;
import com.dasigconnect.backend.model.entity.MediaIntegrityTrigger;
import com.dasigconnect.backend.repository.MediaAssetIntegrityCheckRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.MediaIntegrityTarget;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MediaIntegrityServiceTest {

    private static final String HELLO_SHA256 =
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

    @Mock
    private MediaAssetRepository mediaAssetRepository;
    @Mock
    private MediaAssetIntegrityCheckRepository checkRepository;
    @Mock
    private SupabaseStorageService storageService;
    @Mock
    private MediaIntegrityRecordService recordService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MediaIntegrityAlertService alertService;

    private MediaIntegrityService service;

    @BeforeEach
    void setUp() {
        service = new MediaIntegrityService(
                mediaAssetRepository,
                checkRepository,
                storageService,
                recordService,
                auditLogService,
                userRepository,
                alertService);
    }

    @Test
    void verifyManual_streamsObjectAndRecordsSha256() {
        UUID assetId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        MediaIntegrityTarget target =
                new MediaIntegrityTarget(assetId, institutionId, "https://storage.example/hello.txt", "AST-001");
        JwtUserDetails user = new JwtUserDetails(UUID.randomUUID(), "user@example.com", "contributor", institutionId);
        MediaIntegrityResultDto recorded = result(assetId, HELLO_SHA256, MediaIntegrityStatus.VERIFIED);
        when(mediaAssetRepository.findIntegrityTargetById(assetId)).thenReturn(Optional.of(target));
        when(storageService.openObject(target.storageUrl()))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));
        when(recordService.record(
                eq(assetId),
                eq(institutionId),
                eq(HELLO_SHA256),
                eq(null),
                eq(MediaIntegrityTrigger.MANUAL),
                eq(null)))
                .thenReturn(new MediaIntegrityRecordOutcome(
                        recorded, MediaIntegrityStatus.PENDING, false));

        var result = service.verifyManual(assetId, user);

        assertThat(result.getContentSha256()).isEqualTo(HELLO_SHA256);
        verify(recordService).record(
                assetId,
                institutionId,
                HELLO_SHA256,
                null,
                MediaIntegrityTrigger.MANUAL,
                null);
    }

    @Test
    void verifyManual_missingObjectRecordsMissingStatus() {
        UUID assetId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        MediaIntegrityTarget target =
                new MediaIntegrityTarget(assetId, institutionId, "https://storage.example/missing.jpg", "AST-002");
        JwtUserDetails user = new JwtUserDetails(UUID.randomUUID(), "user@example.com", "validator", institutionId);
        MediaIntegrityResultDto recorded = result(assetId, null, MediaIntegrityStatus.MISSING);
        when(mediaAssetRepository.findIntegrityTargetById(assetId)).thenReturn(Optional.of(target));
        when(storageService.openObject(target.storageUrl()))
                .thenThrow(new SupabaseStorageService.StorageObjectNotFoundException("Stored media object was not found."));
        when(recordService.record(
                eq(assetId),
                eq(institutionId),
                eq(null),
                eq(MediaIntegrityStatus.MISSING),
                eq(MediaIntegrityTrigger.MANUAL),
                any(String.class)))
                .thenReturn(new MediaIntegrityRecordOutcome(
                        recorded, MediaIntegrityStatus.VERIFIED, true));

        var result = service.verifyManual(assetId, user);

        assertThat(result.getIntegrityStatus()).isEqualTo("MISSING");
        verify(alertService).notifyFailure(target, MediaIntegrityStatus.MISSING);
        verify(recordService).record(
                eq(assetId),
                eq(institutionId),
                eq(null),
                eq(MediaIntegrityStatus.MISSING),
                eq(MediaIntegrityTrigger.MANUAL),
                any(String.class));
    }

    @Test
    void verifyManual_crossTenantAssetIsHiddenBeforeStorageAccess() {
        UUID assetId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        MediaIntegrityTarget target =
                new MediaIntegrityTarget(assetId, institutionId, "https://storage.example/private.jpg", "AST-003");
        JwtUserDetails foreignUser =
                new JwtUserDetails(UUID.randomUUID(), "foreign@example.com", "contributor", UUID.randomUUID());
        when(mediaAssetRepository.findIntegrityTargetById(assetId)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.verifyManual(assetId, foreignUser))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");

        verify(storageService, never()).openObject(any());
        verify(recordService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void acknowledgeReview_contributorIsForbidden() {
        UUID assetId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        MediaIntegrityTarget target =
                new MediaIntegrityTarget(assetId, institutionId, "https://storage.example/private.jpg", "AST-004");
        JwtUserDetails contributor =
                new JwtUserDetails(UUID.randomUUID(), "user@example.com", "contributor", institutionId);
        when(mediaAssetRepository.findIntegrityTargetById(assetId)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.acknowledgeReview(assetId, contributor))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");

        verify(recordService, never()).acknowledge(any(), any(), any());
    }

    private MediaIntegrityResultDto result(
            UUID assetId,
            String checksum,
            MediaIntegrityStatus status) {
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        asset.setContentSha256(checksum);
        asset.setIntegrityStatus(status);
        return MediaIntegrityResultDto.from(asset);
    }
}
