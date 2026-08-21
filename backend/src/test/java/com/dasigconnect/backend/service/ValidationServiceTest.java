package com.dasigconnect.backend.service;

import com.dasigconnect.backend.event.SubmissionApprovedEvent;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.repository.ValidationLogRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidationServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private SubmissionMediaAssetRepository submissionMediaAssetRepository;

    @Mock
    private ValidationLogRepository validationLogRepository;

    @Mock
    private ReviewLockService reviewLockService;

    @Mock
    private SlotReservationService slotReservationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ValidationService validationService;

    @Test
    void approve_fastTrackSubmission_skipsSlotReservationConfirmation() {
        UUID adminId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        User admin = user(adminId, "admin@dasigconnect.com", UserRole.administrator);
        Submission submission = submission(submissionId, admin);
        submission.setFastTrack(true);

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionRepository.save(submission)).thenReturn(submission);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        validationService.approve(submissionId,
                new JwtUserDetails(adminId, admin.getEmail(), "administrator", null));

        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.scheduled);
        verify(slotReservationService, never()).confirm(submissionId);
        verify(reviewLockService).release(any(), any());
        verify(eventPublisher).publishEvent(any(SubmissionApprovedEvent.class));
    }

    private static Submission submission(UUID id, User contributor) {
        Institution institution = new Institution();
        institution.setId(UUID.randomUUID());

        Submission submission = new Submission();
        submission.setId(id);
        submission.setContributor(contributor);
        submission.setInstitution(institution);
        submission.setEventTitle("Fast-Track Post");
        submission.setEventDate(LocalDate.parse("2026-08-21"));
        submission.setCaption("Caption");
        submission.setStatus(SubmissionStatus.pending);
        return submission;
    }

    private static User user(UUID id, String email, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        return user;
    }
}
