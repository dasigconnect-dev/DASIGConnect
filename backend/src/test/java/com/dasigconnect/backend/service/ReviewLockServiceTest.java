package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.entity.ReviewLock;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.ReviewLockRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.repository.ValidationLogRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewLockServiceTest {

    @Mock
    private ReviewLockRepository reviewLockRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private ValidationLogRepository validationLogRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReviewLockService reviewLockService;

    @Test
    void getActiveLock_validLock_returnsIt() {
        UUID submissionId = UUID.randomUUID();
        Submission submission = new Submission();
        submission.setId(submissionId);

        User validator = new User();
        validator.setId(UUID.randomUUID());
        validator.setEmail("admin@dasigconnect.local");

        ReviewLock lock = new ReviewLock();
        lock.setId(UUID.randomUUID());
        lock.setSubmission(submission);
        lock.setLockedBy(validator);
        lock.setExpiresAt(Instant.now().plusSeconds(600));

        when(reviewLockRepository.findBySubmissionIdWithLockedBy(submissionId))
                .thenReturn(Optional.of(lock));

        Optional<ReviewLock> result = reviewLockService.getActiveLock(submissionId);

        assertThat(result).isPresent();
        assertThat(result.get().getLockedBy().getEmail()).isEqualTo("admin@dasigconnect.local");
    }

    @Test
    void getActiveLock_expiredLock_returnsEmpty() {
        UUID submissionId = UUID.randomUUID();
        Submission submission = new Submission();
        submission.setId(submissionId);

        User validator = new User();
        validator.setId(UUID.randomUUID());

        ReviewLock lock = new ReviewLock();
        lock.setId(UUID.randomUUID());
        lock.setSubmission(submission);
        lock.setLockedBy(validator);
        lock.setExpiresAt(Instant.now().minusSeconds(60));

        when(reviewLockRepository.findBySubmissionIdWithLockedBy(submissionId))
                .thenReturn(Optional.of(lock));

        Optional<ReviewLock> result = reviewLockService.getActiveLock(submissionId);

        assertThat(result).isEmpty();
    }

    @Test
    void getActiveLock_noLock_returnsEmpty() {
        UUID submissionId = UUID.randomUUID();
        when(reviewLockRepository.findBySubmissionIdWithLockedBy(submissionId))
                .thenReturn(Optional.empty());

        Optional<ReviewLock> result = reviewLockService.getActiveLock(submissionId);

        assertThat(result).isEmpty();
    }
}
