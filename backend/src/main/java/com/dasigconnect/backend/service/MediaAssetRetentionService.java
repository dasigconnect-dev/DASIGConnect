package com.dasigconnect.backend.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;

@Service
public class MediaAssetRetentionService {

    private static final Logger log = LoggerFactory.getLogger(MediaAssetRetentionService.class);

    private final MediaAssetRepository mediaAssetRepository;
    private final AssetTagRepository assetTagRepository;
    private final SupabaseStorageService supabaseStorageService;
    private final int retentionDays;
    private final int batchSize;

    public MediaAssetRetentionService(
            MediaAssetRepository mediaAssetRepository,
            AssetTagRepository assetTagRepository,
            SupabaseStorageService supabaseStorageService,
            @Value("${app.media-assets.deleted-retention-days:30}") int retentionDays,
            @Value("${app.media-assets.purge-batch-size:25}") int batchSize) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.assetTagRepository = assetTagRepository;
        this.supabaseStorageService = supabaseStorageService;
        this.retentionDays = Math.max(retentionDays, 1);
        this.batchSize = Math.min(Math.max(batchSize, 1), 100);
    }

    @Transactional
    public int purgeExpiredDeletedAssets() {
        Instant cutoff = Instant.now().minusSeconds(retentionDays * 24L * 60L * 60L);
        List<MediaAsset> assets = mediaAssetRepository.findDeletedReadyForPurge(cutoff, batchSize);
        for (MediaAsset asset : assets) {
            purge(asset);
        }
        return assets.size();
    }

    private void purge(MediaAsset asset) {
        boolean storageDeleted = supabaseStorageService.deletePublicObject(asset.getStorageUrl());
        assetTagRepository.deleteByMediaAssetId(asset.getId());
        mediaAssetRepository.purgeAiProfile(asset.getId());
        log.info("Purged deleted media asset {} after retention. storageDeleted={}", asset.getId(), storageDeleted);
    }
}
