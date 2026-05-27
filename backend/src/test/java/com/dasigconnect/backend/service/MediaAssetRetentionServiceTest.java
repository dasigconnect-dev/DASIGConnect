package com.dasigconnect.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;

class MediaAssetRetentionServiceTest {

    @Test
    void purgeExpiredDeletedAssets_clearsAiProfileTagsAndStorage() {
        MediaAssetRepository mediaAssetRepository = org.mockito.Mockito.mock(MediaAssetRepository.class);
        AssetTagRepository assetTagRepository = org.mockito.Mockito.mock(AssetTagRepository.class);
        SupabaseStorageService storageService = org.mockito.Mockito.mock(SupabaseStorageService.class);
        MediaAsset asset = new MediaAsset();
        asset.setId(UUID.randomUUID());
        asset.setStorageUrl("https://example.supabase.co/storage/v1/object/public/dasigconnect-media/inst/asset.jpg");
        when(mediaAssetRepository.findDeletedReadyForPurge(any(Instant.class), eq(25))).thenReturn(List.of(asset));
        when(storageService.deletePublicObject(asset.getStorageUrl())).thenReturn(true);
        MediaAssetRetentionService service = new MediaAssetRetentionService(
                mediaAssetRepository,
                assetTagRepository,
                storageService,
                30,
                25);

        int purged = service.purgeExpiredDeletedAssets();

        assertEquals(1, purged);
        verify(storageService).deletePublicObject(asset.getStorageUrl());
        verify(assetTagRepository).deleteByMediaAssetId(asset.getId());
        verify(mediaAssetRepository).purgeAiProfile(asset.getId());
    }
}
