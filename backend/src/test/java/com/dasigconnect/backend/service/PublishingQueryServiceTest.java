package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;

@ExtendWith(MockitoExtension.class)
class PublishingQueryServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private SubmissionMediaAssetRepository submissionMediaAssetRepository;

    @Test
    void claimForPublishing_scheduledSubmission_movesToPublishing() {
        PublishingQueryService service =
                new PublishingQueryService(submissionRepository, submissionMediaAssetRepository);
        UUID submissionId = UUID.randomUUID();
        Submission due = submission(submissionId, SubmissionStatus.scheduled);
        Submission claimed = submission(submissionId, SubmissionStatus.publishing);

        when(submissionRepository.claimForPublishing(
                submissionId,
                SubmissionStatus.scheduled,
                SubmissionStatus.publishing))
                .thenReturn(1);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(claimed));

        Optional<Submission> result = service.claimForPublishing(due);

        assertThat(result).contains(claimed);
        verify(submissionRepository).claimForPublishing(
                submissionId,
                SubmissionStatus.scheduled,
                SubmissionStatus.publishing);
    }

    @Test
    void claimForPublishing_whenAlreadyClaimed_returnsEmpty() {
        PublishingQueryService service =
                new PublishingQueryService(submissionRepository, submissionMediaAssetRepository);
        UUID submissionId = UUID.randomUUID();
        Submission due = submission(submissionId, SubmissionStatus.scheduled);

        when(submissionRepository.claimForPublishing(
                submissionId,
                SubmissionStatus.scheduled,
                SubmissionStatus.publishing))
                .thenReturn(0);

        Optional<Submission> result = service.claimForPublishing(due);

        assertThat(result).isEmpty();
    }

    private static Submission submission(UUID id, SubmissionStatus status) {
        Submission submission = new Submission();
        submission.setId(id);
        submission.setStatus(status);
        return submission;
    }
}
