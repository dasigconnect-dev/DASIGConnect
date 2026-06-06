package com.dasigconnect.backend.service;

import com.dasigconnect.backend.config.IngestionExecutorConfig;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class MediaIntegrityQueueService {

    private static final Logger log = LoggerFactory.getLogger(MediaIntegrityQueueService.class);

    private final Executor executor;
    private final MediaIntegrityService mediaIntegrityService;

    public MediaIntegrityQueueService(
            @Qualifier(IngestionExecutorConfig.INGESTION_EXECUTOR) Executor executor,
            MediaIntegrityService mediaIntegrityService) {
        this.executor = executor;
        this.mediaIntegrityService = mediaIntegrityService;
    }

    public void enqueueIngestCheck(UUID assetId) {
        executor.execute(() -> {
            try {
                mediaIntegrityService.verifyIngest(assetId);
            } catch (Exception ex) {
                log.warn("Initial integrity check failed for asset {}: {}", assetId, ex.getMessage());
            }
        });
    }
}
