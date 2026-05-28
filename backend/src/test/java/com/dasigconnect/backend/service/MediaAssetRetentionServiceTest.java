package com.dasigconnect.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.MediaAssetEmbeddingRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;

class MediaAssetRetentionServiceTest {

    @Test
    void purgeExpiredDeletedAssets_clearsAiProfileTagsAndStorage() {
        MediaAssetRepository mediaAssetRepository = mock(MediaAssetRepository.class);
        MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository = mock(MediaAssetEmbeddingRepository.class);
        AssetTagRepository assetTagRepository = mock(AssetTagRepository.class);
        SupabaseStorageService storageService = mock(SupabaseStorageService.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        TransactionStatus txStatus = mock(TransactionStatus.class);
        when(txManager.getTransaction(any())).thenReturn(txStatus);

        MediaAsset asset = new MediaAsset();
        asset.setId(UUID.randomUUID());
        asset.setStorageUrl("https://example.supabase.co/storage/v1/object/public/dasigconnect-media/inst/asset.jpg");
        when(mediaAssetRepository.findDeletedReadyForPurge(any(Instant.class), eq(25))).thenReturn(List.of(asset));
        when(storageService.deletePublicObject(asset.getStorageUrl())).thenReturn(true);

        MediaAssetRetentionService service = new MediaAssetRetentionService(
                mediaAssetRepository,
                mediaAssetEmbeddingRepository,
                assetTagRepository,
                storageService,
                txManager,
                30,
                25);

        int purged = service.purgeExpiredDeletedAssets();

        assertEquals(1, purged);
        verify(storageService).deletePublicObject(asset.getStorageUrl());
        verify(mediaAssetEmbeddingRepository).deleteByAssetId(asset.getId());
        verify(assetTagRepository).deleteByMediaAssetId(asset.getId());
        verify(mediaAssetRepository).purgeAiProfile(asset.getId());
    }
}
