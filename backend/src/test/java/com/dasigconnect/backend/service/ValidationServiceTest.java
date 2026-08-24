package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.submission.SubmissionUpdateDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.ValidationAction;
import com.dasigconnect.backend.model.entity.ValidationLog;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.repository.ValidationLogRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
    private SubmissionService submissionService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ValidationService validationService;

    private UUID submissionInstitutionId;
    private UUID adminId;
    private UUID contributorId;

    @BeforeEach
    void setUp() {
        submissionInstitutionId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        contributorId = UUID.randomUUID();
        ReflectionTestUtils.setField(validationService, "objectMapper", new ObjectMapper());
    }

    @Test
    void getQueue_callsNetworkWideQueryRegardlessOfCallerInstitution() {
        JwtUserDetails admin = new JwtUserDetails(adminId, "admin@dasigconnect.local", "administrator", null);
        when(submissionRepository.findValidationQueue()).thenReturn(List.of());

        validationService.getQueue(admin);

        verify(submissionRepository).findValidationQueue();
    }

    @Test
    void getHistory_callsNetworkWideQueryRegardlessOfCallerInstitution() {
        JwtUserDetails admin = new JwtUserDetails(adminId, "admin@dasigconnect.local", "administrator", null);
        when(submissionRepository.findValidationHistory()).thenReturn(List.of());

        validationService.getHistory(admin);

        verify(submissionRepository).findValidationHistory();
    }

    @Test
    void approve_administratorCanActOnSubmissionFromAnyInstitution() {
        // Administrator accounts are network-wide (institutionId is always null),
        // so a submission belonging to a different institution than the caller's
        // must still be reviewable — this used to 404 before the scoping fix.
        JwtUserDetails admin = new JwtUserDetails(adminId, "admin@dasigconnect.local", "administrator", null);

        Submission submission = new Submission();
        submission.setId(UUID.randomUUID());
        submission.setStatus(SubmissionStatus.pending);

        Institution institution = new Institution();
        institution.setId(submissionInstitutionId);
        submission.setInstitution(institution);

        User contributor = new User();
        contributor.setId(contributorId);
        submission.setContributor(contributor);

        User adminUser = new User();
        adminUser.setId(adminId);

        when(submissionRepository.findById(submission.getId())).thenReturn(Optional.of(submission));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));

        assertThatCode(() -> validationService.approve(submission.getId(), admin)).doesNotThrowAnyException();
    }

    @Test
    void approve_selfReview_isAllowedAndFlaggedInAuditLog() {
        // A5: self-review is allowed, not blocked — but must be distinctly flagged.
        UUID sharedId = UUID.randomUUID();
        JwtUserDetails admin = new JwtUserDetails(sharedId, "admin@dasigconnect.local", "administrator", null);

        Submission submission = new Submission();
        submission.setId(UUID.randomUUID());
        submission.setStatus(SubmissionStatus.pending);

        Institution institution = new Institution();
        institution.setId(submissionInstitutionId);
        submission.setInstitution(institution);

        User contributor = new User();
        contributor.setId(sharedId); // same identity as the reviewing admin
        submission.setContributor(contributor);

        User adminUser = new User();
        adminUser.setId(sharedId);

        when(submissionRepository.findById(submission.getId())).thenReturn(Optional.of(submission));
        when(userRepository.findById(sharedId)).thenReturn(Optional.of(adminUser));

        validationService.approve(submission.getId(), admin);

        ArgumentCaptor<ValidationLog> captor = ArgumentCaptor.forClass(ValidationLog.class);
        verify(validationLogRepository).save(captor.capture());
        assertThat(captor.getValue().isSelfReview()).isTrue();
    }

    @Test
    void editAndApprove_capturesFieldDiffAndLogsEditedAction() {
        JwtUserDetails admin = new JwtUserDetails(adminId, "admin@dasigconnect.local", "administrator", null);

        Submission submission = new Submission();
        submission.setId(UUID.randomUUID());
        submission.setStatus(SubmissionStatus.pending);
        submission.setEventTitle("Original Title");

        Institution institution = new Institution();
        institution.setId(submissionInstitutionId);
        submission.setInstitution(institution);

        User contributor = new User();
        contributor.setId(contributorId);
        submission.setContributor(contributor);

        User adminUser = new User();
        adminUser.setId(adminId);

        when(submissionRepository.findById(submission.getId())).thenReturn(Optional.of(submission));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(submissionService.applySubmissionEdits(any(), any())).thenAnswer(invocation -> {
            Submission s = invocation.getArgument(0);
            s.setEventTitle("Edited Title");
            return s;
        });

        SubmissionUpdateDto dto = new SubmissionUpdateDto();
        dto.setEventTitle("Edited Title");

        validationService.editAndApprove(submission.getId(), dto, admin);

        ArgumentCaptor<ValidationLog> captor = ArgumentCaptor.forClass(ValidationLog.class);
        verify(validationLogRepository).save(captor.capture());
        ValidationLog entry = captor.getValue();
        assertThat(entry.getAction()).isEqualTo(ValidationAction.edited_and_approved);
        assertThat(entry.getEditDiff()).contains("Original Title").contains("Edited Title");
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.scheduled);
    }
}
