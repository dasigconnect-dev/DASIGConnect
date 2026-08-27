package com.dasigconnect.backend.schedule;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dasigconnect.backend.event.EmbeddingFailureDigestEvent;
import com.dasigconnect.backend.repository.MediaAssetRepository;

/**
 * T-12: Weekly scan of EMBEDDING_FAILED assets.
 * If failed assets exist, dispatches a summary digest notification to the
 * Super Administrator.
 */
@Component
public class EmbeddingFailureDigestJob {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingFailureDigestJob.class);

    private final MediaAssetRepository mediaAssetRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EmbeddingFailureDigestJob(
            MediaAssetRepository mediaAssetRepository,
            ApplicationEventPublisher eventPublisher) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(cron = "${app.schedule.embedding-digest-cron:0 0 9 * * MON}")
    public void scanFailedEmbeddings() {
        log.info("Running EmbeddingFailureDigestJob scan");
        try {
            long failedCount = mediaAssetRepository.countFailedAssets();
            if (failedCount > 0) {
                log.info("Found {} failed media asset embeddings — generating T-12 digest alert", failedCount);
                List<String> sampleNames = mediaAssetRepository.findSampleFailedFilenames();
                eventPublisher.publishEvent(new EmbeddingFailureDigestEvent(failedCount, sampleNames));
            }
        } catch (Exception ex) {
            log.warn("EmbeddingFailureDigestJob scan error: {}", ex.getMessage());
        }
    }
}
