package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    private final MediaProcessingQueueService service = new MediaProcessingQueueService(
            repository, 5, 300, 2, 100);

    @Test
    void enqueue_usesStableVersionAndConfiguredAttemptLimit() {
        UUID assetId = UUID.randomUUID();

        service.enqueue(assetId);

        verify(repository).enqueue(assetId, MediaProcessingQueueService.PROCESSING_VERSION, 5);
    }

    @Test
    void enqueueSubmissionContext_usesStableContextVersion() {
        UUID submissionId = UUID.randomUUID();

        service.enqueueSubmissionContext(submissionId);

        verify(repository).enqueueSubmissionContext(
                submissionId, MediaProcessingQueueService.CONTEXT_VERSION, 5);
    }

    @Test
    void enqueueImageOnly_usesIndependentStableVersion() {
        UUID assetId = UUID.randomUUID();

        service.enqueueImageOnly(assetId);

        verify(repository).enqueueImageOnly(
                assetId, MediaProcessingQueueService.IMAGE_EMBEDDING_VERSION, 5);
    }

    @Test
    void enqueueImageOnlyAfterCommit_defersQueueWriteUntilTransactionCommits() {
        UUID assetId = UUID.randomUUID();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.enqueueImageOnlyAfterCommit(assetId);
            org.mockito.Mockito.verify(repository, org.mockito.Mockito.never())
                    .enqueueImageOnly(any(), any(), org.mockito.ArgumentMatchers.anyInt());

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);
            synchronizations.getFirst().afterCommit();

            verify(repository).enqueueImageOnly(
                    assetId, MediaProcessingQueueService.IMAGE_EMBEDDING_VERSION, 5);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
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
        when(repository.findClaimedBatchInPriorityOrder(
                "worker", MediaProcessingJobStatus.PROCESSING.name())).thenReturn(List.of());

        assertThat(service.claimBatch("worker", 100, false, true)).isEmpty();

        verify(repository).claimBatch(
                eq("worker"), any(), any(), eq(10), eq(2), eq(false), eq(true));
        verify(repository).findClaimedBatchInPriorityOrder(
                "worker", MediaProcessingJobStatus.PROCESSING.name());
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

    @Test
    void availableBackfillSlots_respectsConfiguredQueueCapacity() {
        when(repository.countActiveJobs()).thenReturn(97L);

        assertThat(service.availableBackfillSlots(10)).isEqualTo(3);
    }

    @Test
    void retryDead_resetsOnlyDeadLetterJobs() {
        UUID jobId = UUID.randomUUID();
        when(repository.retryDead(eq(jobId), any())).thenReturn(1);

        service.retryDead(jobId);

        verify(repository).retryDead(eq(jobId), any());
    }

    @Test
    void retryDead_rejectsJobOutsideDeadLetterState() {
        UUID jobId = UUID.randomUUID();
        when(repository.retryDead(eq(jobId), any())).thenReturn(0);

        assertThatThrownBy(() -> service.retryDead(jobId))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
