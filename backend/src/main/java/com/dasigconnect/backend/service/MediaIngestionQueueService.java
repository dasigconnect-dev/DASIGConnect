package com.dasigconnect.backend.service;

import com.dasigconnect.backend.config.IngestionExecutorConfig;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetStatus;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import java.util.concurrent.Executor;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Bounded ingestion queue for media enrichment (UC-4.2, ADR-0002). Upload (and the
 * reconciliation job) enqueue assets here instead of firing unbounded {@code @Async}
 * tasks; the bounded executor drains them at a controlled rate so a large dump cannot
 * exhaust threads or the HikariCP-5 pool.
 *
 * <p>Idempotency: the worker re-checks the asset before doing expensive work and skips
 * assets that are already {@code READY}, so a re-enqueue or an overlap with the
 * reconciliation job does not redo completed work. The underlying embedding writes are
 * also upserts keyed by {@code (asset_id, embedding_type)}.
 */
@Service
public class MediaIngestionQueueService {

    private static final Logger log = LoggerFactory.getLogger(MediaIngestionQueueService.class);

    private final Executor executor;
    private final AIClassificationService aiClassificationService;
    private final MediaQualityAnalysisService mediaQualityAnalysisService;
    private final MediaAssetRepository mediaAssetRepository;

    public MediaIngestionQueueService(
            @Qualifier(IngestionExecutorConfig.INGESTION_EXECUTOR) Executor executor,
            AIClassificationService aiClassificationService,
            MediaQualityAnalysisService mediaQualityAnalysisService,
            MediaAssetRepository mediaAssetRepository) {
        this.executor = executor;
        this.aiClassificationService = aiClassificationService;
        this.mediaQualityAnalysisService = mediaQualityAnalysisService;
        this.mediaAssetRepository = mediaAssetRepository;
    }

    /** Submits an asset for classification + embedding on the bounded worker pool. */
    public void enqueue(UUID assetId, String storageUrl) {
        executor.execute(() -> process(assetId, storageUrl));
    }

    /** Package-private so it can be unit-tested with a synchronous executor. */
    void process(UUID assetId, String storageUrl) {
        try {
            MediaAsset asset = mediaAssetRepository.findActiveById(assetId).orElse(null);
            if (asset == null) {
                log.debug("Ingestion skipped: asset {} not found or deleted", assetId);
                return;
            }
            if (asset.getStatus() == MediaAssetStatus.READY) {
                log.debug("Ingestion skipped: asset {} already READY", assetId);
                return;
            }
            if (asset.getFileType() != null && asset.getFileType().isImage()) {
                mediaQualityAnalysisService.analyzeImage(assetId, storageUrl);
            }
            aiClassificationService.classifyAndEmbed(assetId, storageUrl);
        } catch (Exception e) {
            log.error("Ingestion task failed for asset {}: {}", assetId, e.getMessage());
        }
    }
}
