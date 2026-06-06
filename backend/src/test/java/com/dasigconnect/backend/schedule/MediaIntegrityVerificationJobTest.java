package com.dasigconnect.backend.schedule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.MediaIntegrityTarget;
import com.dasigconnect.backend.service.MediaIntegrityService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class MediaIntegrityVerificationJobTest {

    @Mock
    private MediaAssetRepository mediaAssetRepository;
    @Mock
    private MediaIntegrityService mediaIntegrityService;

    @Test
    void verifyDueAssets_capsConfiguredBatchAndProcessesTargets() {
        MediaIntegrityTarget target = new MediaIntegrityTarget(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "https://storage.example/asset.jpg",
                "AST-100");
        when(mediaAssetRepository.findDueIntegrityTargets(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(target));
        MediaIntegrityVerificationJob job = new MediaIntegrityVerificationJob(
                mediaAssetRepository,
                mediaIntegrityService,
                100,
                30,
                24);

        job.verifyDueAssets();

        verify(mediaAssetRepository).findDueIntegrityTargets(
                any(),
                any(),
                eq(org.springframework.data.domain.PageRequest.of(0, 25)));
        verify(mediaIntegrityService).verifyScheduled(target);
    }
}
