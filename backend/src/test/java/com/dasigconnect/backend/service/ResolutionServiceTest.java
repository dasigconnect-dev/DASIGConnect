package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.dasigconnect.backend.model.dto.resolution.FailedPublicationPageDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.PublicationAttempt;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.repository.PublicationAttemptRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;

@ExtendWith(MockitoExtension.class)
class ResolutionServiceTest {

    @Mock private SubmissionRepository submissionRepository;
    @Mock private PublicationAttemptRepository publicationAttemptRepository;
    @InjectMocks private ResolutionService resolutionService;

    @Test
    void getFailurePage_clampsPaging_normalizesSearch_andBatchesLatestAttempts() {
        Submission first = failure("First");
        Submission second = failure("Second");
        PublicationAttempt latest = attempt(first, 3, "Latest failure");
        PageRequest expectedPage = PageRequest.of(0, 50);

        when(submissionRepository.findPublishFailurePage("token", expectedPage))
                .thenReturn(new PageImpl<>(List.of(first, second), expectedPage, 72));
        when(publicationAttemptRepository.findLatestBySubmissionIds(any()))
                .thenReturn(List.of(latest));
        when(submissionRepository.countPublishFailures()).thenReturn(80L);

        FailedPublicationPageDto result = resolutionService.getFailurePage(-4, 500, "  ToKeN  ");

        assertThat(result.page()).isZero();
        assertThat(result.pageSize()).isEqualTo(50);
        assertThat(result.totalCount()).isEqualTo(72);
        assertThat(result.failureCount()).isEqualTo(80);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).getLastError()).isEqualTo("Latest failure");
        assertThat(result.items().get(1).getLastError()).isNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> ids = ArgumentCaptor.forClass(List.class);
        verify(publicationAttemptRepository).findLatestBySubmissionIds(ids.capture());
        assertThat(ids.getValue()).containsExactly(first.getId(), second.getId());
    }

    @Test
    void getFailurePage_emptyPage_skipsAttemptLookup() {
        Pageable pageable = PageRequest.of(2, 20);
        when(submissionRepository.findPublishFailurePage("", pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        FailedPublicationPageDto result = resolutionService.getFailurePage(2, 0, null);

        assertThat(result.items()).isEmpty();
        assertThat(result.pageSize()).isEqualTo(20);
        verifyNoInteractions(publicationAttemptRepository);
    }

    private static Submission failure(String title) {
        Institution institution = new Institution();
        institution.setId(UUID.randomUUID());
        institution.setName("CIT-U");

        Submission submission = new Submission();
        submission.setId(UUID.randomUUID());
        submission.setEventTitle(title);
        submission.setStatus(SubmissionStatus.publish_failed);
        submission.setInstitution(institution);
        return submission;
    }

    private static PublicationAttempt attempt(Submission submission, int number, String error) {
        PublicationAttempt attempt = new PublicationAttempt();
        attempt.setId(UUID.randomUUID());
        attempt.setSubmission(submission);
        attempt.setAttemptNumber(number);
        attempt.setAttemptedAt(Instant.parse("2026-09-24T00:00:00Z"));
        attempt.setResult("FAILED");
        attempt.setErrorDetail(error);
        return attempt;
    }
}
