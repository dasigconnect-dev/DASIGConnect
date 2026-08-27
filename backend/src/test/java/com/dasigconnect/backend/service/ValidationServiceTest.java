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

    @Mock
    private AuditLogService auditLogService;

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
    void approve_fastTrackSubmission_skipsSlotConfirmationAndFlagsAuditLog() {
        // Fast-Track (Live Event) submissions never get a slot reservation created
        // (see SubmissionService.create()), so confirming one would throw
        // IllegalStateException and roll back the whole approval. Approve must skip
        // slot confirmation for fast-track submissions and flag the audit log.
        JwtUserDetails admin = new JwtUserDetails(adminId, "admin@dasigconnect.local", "administrator", null);

        Submission submission = new Submission();
        submission.setId(UUID.randomUUID());
        submission.setStatus(SubmissionStatus.pending);
        submission.setFastTrack(true);

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

        verify(slotReservationService, org.mockito.Mockito.never()).confirm(any());
        ArgumentCaptor<ValidationLog> captor = ArgumentCaptor.forClass(ValidationLog.class);
        verify(validationLogRepository).save(captor.capture());
        assertThat(captor.getValue().isFastTrack()).isTrue();
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.scheduled);
    }

    @Test
    void approve_standardSubmission_stillConfirmsSlot() {
        JwtUserDetails admin = new JwtUserDetails(adminId, "admin@dasigconnect.local", "administrator", null);

        Submission submission = new Submission();
        submission.setId(UUID.randomUUID());
        submission.setStatus(SubmissionStatus.pending);
        submission.setFastTrack(false);

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

        validationService.approve(submission.getId(), admin);

        verify(slotReservationService).confirm(submission.getId());
    }

    @Test
    void edit_keepsSubmissionInReviewAndLogsStandaloneEditedAction() {
        // A9: a standalone edit records its diff but does NOT transition the
        // submission out of IN_REVIEW and never confirms a slot or fires approval.
        JwtUserDetails admin = new JwtUserDetails(adminId, "admin@dasigconnect.local", "administrator", null);

        Submission submission = new Submission();
        submission.setId(UUID.randomUUID());
        submission.setStatus(SubmissionStatus.in_review);
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

        validationService.edit(submission.getId(), dto, admin);

        ArgumentCaptor<ValidationLog> captor = ArgumentCaptor.forClass(ValidationLog.class);
        verify(validationLogRepository).save(captor.capture());
        ValidationLog entry = captor.getValue();
        assertThat(entry.getAction()).isEqualTo(ValidationAction.edited);
        assertThat(entry.getEditDiff()).contains("Original Title").contains("Edited Title");
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.in_review);
        verify(slotReservationService, org.mockito.Mockito.never()).confirm(any());
        verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any());
    }

    private Submission inReviewSubmission() {
        Submission submission = new Submission();
        submission.setId(UUID.randomUUID());
        submission.setStatus(SubmissionStatus.in_review);
        Institution institution = new Institution();
        institution.setId(submissionInstitutionId);
        submission.setInstitution(institution);
        User contributor = new User();
        contributor.setId(contributorId);
        submission.setContributor(contributor);
        return submission;
    }

    @Test
    void detachReviewMedia_onInReview_delegatesAndLogsEdited() {
        JwtUserDetails admin = new JwtUserDetails(adminId, "admin@dasigconnect.local", "administrator", null);
        Submission submission = inReviewSubmission();
        UUID assetId = UUID.randomUUID();
        User adminUser = new User();
        adminUser.setId(adminId);
        when(submissionRepository.findById(submission.getId())).thenReturn(Optional.of(submission));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));

        validationService.detachReviewMedia(submission.getId(), assetId, admin);

        verify(reviewLockService).assertCallerHoldsLock(submission.getId(), admin);
        verify(submissionService).detachAssetFrom(submission, assetId);
        ArgumentCaptor<ValidationLog> log = ArgumentCaptor.forClass(ValidationLog.class);
        verify(validationLogRepository).save(log.capture());
        assertThat(log.getValue().getAction()).isEqualTo(ValidationAction.edited);
    }

    @Test
    void reorderReviewMedia_rejectsSubmissionThatIsNotReviewable() {
        JwtUserDetails admin = new JwtUserDetails(adminId, "admin@dasigconnect.local", "administrator", null);
        Submission submission = inReviewSubmission();
        submission.setStatus(SubmissionStatus.scheduled);
        when(submissionRepository.findById(submission.getId())).thenReturn(Optional.of(submission));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                validationService.reorderReviewMedia(submission.getId(),
                        new com.dasigconnect.backend.model.dto.submission.SubmissionMediaOrderDto(), admin))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(submissionService, org.mockito.Mockito.never()).reorderMediaOf(any(), any());
    }

    @Test
    void approve_afterSessionEdit_recordsEditedApprovalAndFiresEditedEvent() {
        // A10/A11: approving after one or more standalone edits this session records
        // the terminal action as `approved` with the combined before/after diff
        // attached (marking it an edited approval) and notifies the contributor
        // that changes were made.
        JwtUserDetails admin = new JwtUserDetails(adminId, "admin@dasigconnect.local", "administrator", null);

        Submission submission = new Submission();
        submission.setId(UUID.randomUUID());
        submission.setStatus(SubmissionStatus.in_review);

        Institution institution = new Institution();
        institution.setId(submissionInstitutionId);
        submission.setInstitution(institution);

        User contributor = new User();
        contributor.setId(contributorId);
        submission.setContributor(contributor);

        User adminUser = new User();
        adminUser.setId(adminId);

        ValidationLog lockLog = new ValidationLog();
        lockLog.setAction(ValidationAction.lock_acquired);
        ValidationLog editLog = new ValidationLog();
        editLog.setAction(ValidationAction.edited);
        editLog.setEditDiff("{\"caption\":{\"from\":\"old\",\"to\":\"new\"}}");

        when(submissionRepository.findById(submission.getId())).thenReturn(Optional.of(submission));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(validationLogRepository.findBySubmissionIdOrderByCreatedAtAsc(submission.getId()))
                .thenReturn(List.of(lockLog, editLog));

        validationService.approve(submission.getId(), admin);

        ArgumentCaptor<ValidationLog> captor = ArgumentCaptor.forClass(ValidationLog.class);
        verify(validationLogRepository).save(captor.capture());
        ValidationLog entry = captor.getValue();
        assertThat(entry.getAction()).isEqualTo(ValidationAction.approved);
        assertThat(entry.getEditDiff()).contains("caption").contains("old").contains("new");
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.scheduled);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .isInstanceOf(com.dasigconnect.backend.event.SubmissionApprovedEvent.class);
        assertThat(((com.dasigconnect.backend.event.SubmissionApprovedEvent) eventCaptor.getValue()).edited())
                .isTrue();
    }
}
