package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.entity.MediaProcessingJob;
import com.dasigconnect.backend.model.entity.MediaProcessingJobStatus;
import com.dasigconnect.backend.repository.MediaProcessingJobRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class MediaProcessingQueueServiceTest {

    private final MediaProcessingJobRepository repository = mock(MediaProcessingJobRepository.class);
    private final MediaProcessingQueueService service = new MediaProcessingQueueService(repository, 5, 300);

    @Test
    void enqueue_usesStableVersionAndConfiguredAttemptLimit() {
        UUID assetId = UUID.randomUUID();

        service.enqueue(assetId);

        verify(repository).enqueue(assetId, MediaProcessingQueueService.PROCESSING_VERSION, 5);
    }

    @Test
    void enqueueAfterCommit_defersQueueWriteUntilOwningTransactionCommits() {
        UUID assetId = UUID.randomUUID();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.enqueueAfterCommit(assetId);
            org.mockito.Mockito.verify(repository, org.mockito.Mockito.never())
                    .enqueue(any(), any(), org.mockito.ArgumentMatchers.anyInt());

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);
            synchronizations.getFirst().afterCommit();

            verify(repository).enqueue(assetId, MediaProcessingQueueService.PROCESSING_VERSION, 5);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void claimBatch_capsRequestedWork() {
        when(repository.findByClaimedByAndStatusOrderByCreatedAtAsc(
                "worker", MediaProcessingJobStatus.PROCESSING)).thenReturn(List.of());

        assertThat(service.claimBatch("worker", 100)).isEmpty();

        verify(repository).claimBatch(eq("worker"), any(), any(), eq(10));
    }

    @Test
    void fail_exhaustedJobMovesToDeadLetterAndSanitizesError() {
        MediaProcessingJob job = mock(MediaProcessingJob.class);
        UUID jobId = UUID.randomUUID();
        when(job.getId()).thenReturn(jobId);
        when(job.getAttemptCount()).thenReturn(5);
        when(job.getMaxAttempts()).thenReturn(5);
        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);

        service.fail(job, "worker", new RuntimeException("provider\nfailed"));

        verify(repository).releaseAfterFailure(
                eq(jobId), eq("worker"), eq(MediaProcessingJobStatus.DEAD.name()),
                any(), errorCaptor.capture(), any());
        assertThat(errorCaptor.getValue()).isEqualTo("provider failed");
    }
}
