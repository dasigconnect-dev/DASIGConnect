package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetIntegrityCheck;
import com.dasigconnect.backend.model.entity.MediaIntegrityStatus;
import com.dasigconnect.backend.model.entity.MediaIntegrityTrigger;
import com.dasigconnect.backend.model.entity.MediaIntegrityReviewStatus;
import com.dasigconnect.backend.repository.MediaAssetIntegrityCheckRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaIntegrityRecordServiceTest {

    @Mock
    private MediaAssetRepository mediaAssetRepository;
    @Mock
    private MediaAssetIntegrityCheckRepository checkRepository;

    private MediaIntegrityRecordService service;

    @BeforeEach
    void setUp() {
        service = new MediaIntegrityRecordService(mediaAssetRepository, checkRepository);
    }

    @Test
    void record_firstSuccessfulCheckEstablishesImmutableBaseline() {
        UUID institutionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        String observed = "a".repeat(64);
        MediaAsset asset = asset(assetId, institutionId);
        when(mediaAssetRepository.findActiveByIdForIntegrityUpdate(assetId))
                .thenReturn(Optional.of(asset));

        var result = service.record(
                assetId,
                institutionId,
                observed,
                null,
                MediaIntegrityTrigger.INGEST,
                null);

        assertThat(result.result().getIntegrityStatus()).isEqualTo("VERIFIED");
        assertThat(result.alertRequired()).isFalse();
        assertThat(asset.getContentSha256()).isEqualTo(observed);
        assertThat(asset.getChecksumGeneratedAt()).isNotNull();
        assertThat(asset.getIntegrityCheckedAt()).isNotNull();

        ArgumentCaptor<MediaAssetIntegrityCheck> checkCaptor =
                ArgumentCaptor.forClass(MediaAssetIntegrityCheck.class);
        verify(checkRepository).save(checkCaptor.capture());
        assertThat(checkCaptor.getValue().getExpectedSha256()).isEqualTo(observed);
        assertThat(checkCaptor.getValue().getObservedSha256()).isEqualTo(observed);
        assertThat(checkCaptor.getValue().getStatus()).isEqualTo(MediaIntegrityStatus.VERIFIED);
    }

    @Test
    void record_changedContentReportsMismatchWithoutReplacingBaseline() {
        UUID institutionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        String expected = "a".repeat(64);
        String observed = "b".repeat(64);
        MediaAsset asset = asset(assetId, institutionId);
        asset.setContentSha256(expected);
        when(mediaAssetRepository.findActiveByIdForIntegrityUpdate(assetId))
                .thenReturn(Optional.of(asset));

        var result = service.record(
                assetId,
                institutionId,
                observed,
                null,
                MediaIntegrityTrigger.MANUAL,
                null);

        assertThat(result.result().getIntegrityStatus()).isEqualTo("MISMATCH");
        assertThat(result.alertRequired()).isTrue();
        assertThat(asset.getContentSha256()).isEqualTo(expected);
        assertThat(asset.getIntegrityFailureReason()).contains("does not match");
        assertThat(asset.getIntegrityReviewStatus()).isEqualTo(MediaIntegrityReviewStatus.OPEN);
        verify(mediaAssetRepository).save(any(MediaAsset.class));
    }

    @Test
    void acknowledge_activeFailureMarksReviewOwned() {
        UUID institutionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        MediaAsset asset = asset(assetId, institutionId);
        asset.setIntegrityStatus(MediaIntegrityStatus.MISSING);
        asset.setIntegrityReviewStatus(MediaIntegrityReviewStatus.OPEN);
        when(mediaAssetRepository.findActiveByIdForIntegrityUpdate(assetId))
                .thenReturn(Optional.of(asset));

        var result = service.acknowledge(assetId, institutionId, actorId);

        assertThat(result.getIntegrityReviewStatus()).isEqualTo("ACKNOWLEDGED");
        assertThat(asset.getIntegrityReviewAcknowledgedAt()).isNotNull();
        assertThat(asset.getIntegrityReviewAcknowledgedBy()).isEqualTo(actorId);
    }

    private MediaAsset asset(UUID assetId, UUID institutionId) {
        Institution institution = new Institution();
        institution.setId(institutionId);
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        asset.setInstitution(institution);
        asset.setIntegrityStatus(MediaIntegrityStatus.PENDING);
        asset.setIntegrityReviewStatus(MediaIntegrityReviewStatus.NONE);
        return asset;
    }
}
