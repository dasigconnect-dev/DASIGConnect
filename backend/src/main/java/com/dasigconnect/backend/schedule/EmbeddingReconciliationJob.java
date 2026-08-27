package com.dasigconnect.backend.schedule;

import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.service.AIClassificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Reconciles media assets that do not have embeddings yet (UC-3.3).
 *
 * This covers both previously classified assets and older/unscanned assets
 * that can still be embedded from metadata such as filename, asset code,
 * media type, category, description, and manual tags.
 * Runs every 5 minutes. Idempotent.
 */
@Component
public class EmbeddingReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingReconciliationJob.class);

    private final MediaAssetRepository mediaAssetRepository;
    private final AssetTagRepository assetTagRepository;
    private final AIClassificationService aiClassificationService;
    private final com.dasigconnect.backend.service.ScheduledJobHealthService scheduledJobHealthService;

    public EmbeddingReconciliationJob(MediaAssetRepository mediaAssetRepository,
                                      AssetTagRepository assetTagRepository,
                                      AIClassificationService aiClassificationService,
                                      com.dasigconnect.backend.service.ScheduledJobHealthService scheduledJobHealthService) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.assetTagRepository = assetTagRepository;
        this.aiClassificationService = aiClassificationService;
        this.scheduledJobHealthService = scheduledJobHealthService;
    }

    @Scheduled(fixedDelay = 300_000)
    public void reconcile() {
        Instant startedAt = Instant.now();
        try {
            reconcilePendingEmbeddings();
            scheduledJobHealthService.recordSuccess("EmbeddingReconciliationJob", startedAt);
        } catch (Exception ex) {
            log.error("EmbeddingReconciliationJob failed: {}", ex.getMessage(), ex);
            scheduledJobHealthService.recordFailure("EmbeddingReconciliationJob", startedAt, ex);
        }
    }

    private void reconcilePendingEmbeddings() {
        List<MediaAsset> pending = mediaAssetRepository.findNeedingEmbedding();
        if (pending.isEmpty()) {
            return;
        }

        log.info("EmbeddingReconciliationJob: found {} assets needing embedding", pending.size());

        for (MediaAsset asset : pending) {
            try {
                if (asset.getFileType() != null && asset.getFileType().isImage()) {
                    aiClassificationService.classifyAndEmbed(asset.getId(), asset.getStorageUrl());
                    continue;
                }

                List<String> tagLabels = assetTagRepository
                        .findByMediaAssetIdOrderByCreatedAtAsc(asset.getId())
                        .stream()
                        .map(t -> t.getLabel())
                        .toList();

                String embeddingText = AIClassificationService.buildEmbeddingText(asset, tagLabels);
                aiClassificationService.generateAndStoreEmbedding(asset.getId(), embeddingText);
            } catch (Exception e) {
                log.warn("Reconciliation failed for asset {}: {}", asset.getId(), e.getMessage());
            }
        }
    }
}
