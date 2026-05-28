package com.dasigconnect.backend.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.MediaAssetEmbeddingRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;

@Service
public class MediaAssetRetentionService {

    private static final Logger log = LoggerFactory.getLogger(MediaAssetRetentionService.class);

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository;
    private final AssetTagRepository assetTagRepository;
    private final SupabaseStorageService supabaseStorageService;
    private final TransactionTemplate txTemplate;
    private final int retentionDays;
    private final int batchSize;

    public MediaAssetRetentionService(
            MediaAssetRepository mediaAssetRepository,
            MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository,
            AssetTagRepository assetTagRepository,
            SupabaseStorageService supabaseStorageService,
            PlatformTransactionManager transactionManager,
            @Value("${app.media-assets.deleted-retention-days:30}") int retentionDays,
            @Value("${app.media-assets.purge-batch-size:25}") int batchSize) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.mediaAssetEmbeddingRepository = mediaAssetEmbeddingRepository;
        this.assetTagRepository = assetTagRepository;
        this.supabaseStorageService = supabaseStorageService;
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.retentionDays = Math.max(retentionDays, 1);
        this.batchSize = Math.min(Math.max(batchSize, 1), 100);
    }

    public int purgeExpiredDeletedAssets() {
        Instant cutoff = Instant.now().minusSeconds(retentionDays * 24L * 60L * 60L);

        // Short read transaction to fetch candidates — connection released immediately after
        List<MediaAsset> assets = txTemplate.execute(status ->
                mediaAssetRepository.findDeletedReadyForPurge(cutoff, batchSize));
        if (assets == null || assets.isEmpty()) return 0;

        int purged = 0;
        for (MediaAsset asset : assets) {
            UUID assetId = asset.getId();
            String storageUrl = asset.getStorageUrl();

            // Storage deletion runs with no DB connection held
            boolean storageDeleted = supabaseStorageService.deletePublicObject(storageUrl);

            // Short write transaction for DB cleanup only
            txTemplate.execute(status -> {
                mediaAssetEmbeddingRepository.deleteByAssetId(assetId);
                assetTagRepository.deleteByMediaAssetId(assetId);
                mediaAssetRepository.purgeAiProfile(assetId);
                return null;
            });

            log.info("Purged deleted media asset {} after retention. storageDeleted={}", assetId, storageDeleted);
            purged++;
        }
        return purged;
    }
}
