package com.dasigconnect.backend.service;

import com.dasigconnect.backend.config.JacksonConfig;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        ReflectionTestUtils.setField(
                validationService,
                "objectMapper",
                new JacksonConfig().objectMapper());
        ReflectionTestUtils.setField(validationService, "captionMajorChangeRatio", 0.30);
    }

    private JwtUserDetails moderator() {
        return new JwtUserDetails(adminId, "admin@dasigconnect.local", "moderator", null);
    }

    private JwtUserDetails admin() {
        return new JwtUserDetails(adminId, "admin@dasigconnect.local", "admin", null);
    }

    private void stubInReview(Submission submission) {
        User adminUser = new User();
        adminUser.setId(adminId);
        when(submissionRepository.findById(submission.getId())).thenReturn(Optional.of(submission));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
    }

    private ValidationLog savedLog() {
        ArgumentCaptor<ValidationLog> captor = ArgumentCaptor.forClass(ValidationLog.class);
        verify(validationLogRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void edit_titleOnly_classifiesQuiet() {
        Submission submission = inReviewSubmission();
        submission.setEventTitle("Original");
        stubInReview(submission);
        when(submissionService.applySubmissionEdits(any(), any(), any())).thenAnswer(i -> {
            Submission s = i.getArgument(0);
            s.setEventTitle("Fixed");
            return s;
        });
        SubmissionUpdateDto dto = new SubmissionUpdateDto();
        dto.setEventTitle("Fixed");

        validationService.edit(submission.getId(), dto, moderator());

        assertThat(savedLog().getEditSeverity()).isEqualTo("quiet");
    }

    @Test
    void edit_fastTrackChangeAsModerator_throwsForbidden() {
        Submission submission = inReviewSubmission();
        stubInReview(submission);
        SubmissionUpdateDto dto = new SubmissionUpdateDto();
        dto.setFastTrack(false);

        assertThatThrownBy(() -> validationService.edit(submission.getId(), dto, moderator()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(e -> assertThat(
                        ((org.springframework.web.server.ResponseStatusException) e).getStatusCode())
                        .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));

        verify(submissionService, never()).applySubmissionEdits(any(), any(), any());
    }

    @Test
    void edit_fastTrackChangeAsAdmin_isAllowed() {
        Submission submission = inReviewSubmission();
        stubInReview(submission);
        when(submissionService.applySubmissionEdits(any(), any(), any())).thenAnswer(i -> i.getArgument(0));
        SubmissionUpdateDto dto = new SubmissionUpdateDto();
        dto.setFastTrack(false);

        validationService.edit(submission.getId(), dto, admin());

        verify(submissionService).applySubmissionEdits(any(), any(), any());
    }

    @Test
    void edit_withoutFastTrackField_moderatorIsUnaffectedByTheGuard() {
        Submission submission = inReviewSubmission();
        submission.setEventTitle("Original");
        stubInReview(submission);
        when(submissionService.applySubmissionEdits(any(), any(), any())).thenAnswer(i -> {
            Submission s = i.getArgument(0);
            s.setEventTitle("Fixed");
            return s;
        });
        SubmissionUpdateDto dto = new SubmissionUpdateDto();
        dto.setEventTitle("Fixed");

        validationService.edit(submission.getId(), dto, moderator());

        verify(submissionService).applySubmissionEdits(any(), any(), any());
    }

    @Test
    void edit_scheduledAtChange_classifiesFlagged() {
        Submission submission = inReviewSubmission();
        stubInReview(submission);
        when(submissionService.applySubmissionEdits(any(), any(), any())).thenAnswer(i -> {
            Submission s = i.getArgument(0);
            s.setScheduledAt(java.time.Instant.parse("2026-10-01T09:00:00Z"));
            return s;
        });

        validationService.edit(submission.getId(), new SubmissionUpdateDto(), moderator());

        assertThat(savedLog().getEditSeverity()).isEqualTo("flagged");
    }

    @Test
    void edit_majorCaptionReword_classifiesFlagged() {
        Submission submission = inReviewSubmission();
        submission.setCaption("the quick brown fox jumps over the lazy dog");
        stubInReview(submission);
        when(submissionService.applySubmissionEdits(any(), any(), any())).thenAnswer(i -> {
            Submission s = i.getArgument(0);
            s.setCaption("a completely different sentence with new words entirely");
            return s;
        });
        SubmissionUpdateDto dto = new SubmissionUpdateDto();
        dto.setCaption("a completely different sentence with new words entirely");

        validationService.edit(submission.getId(), dto, moderator());

        assertThat(savedLog().getEditSeverity()).isEqualTo("flagged");
    }

    @Test
    void edit_minorCaptionTweak_classifiesQuiet() {
        Submission submission = inReviewSubmission();
        submission.setCaption("Join us this Saturday for the community outreach event at the plaza");
        stubInReview(submission);
        when(submissionService.applySubmissionEdits(any(), any(), any())).thenAnswer(i -> {
            Submission s = i.getArgument(0);
            s.setCaption("Join us this Sunday for the community outreach event at the plaza");
            return s;
        });
        SubmissionUpdateDto dto = new SubmissionUpdateDto();
        dto.setCaption("Join us this Sunday for the community outreach event at the plaza");

        validationService.edit(submission.getId(), dto, moderator());

        assertThat(savedLog().getEditSeverity()).isEqualTo("quiet");
    }

    @Test
    void attachReviewLibraryAsset_logsMediaAddedWithJustification() {
        Submission submission = inReviewSubmission();
        UUID assetId = UUID.randomUUID();
        stubInReview(submission);

        validationService.attachReviewLibraryAsset(submission.getId(), assetId, "  needed a wider crowd shot  ", moderator());

        verify(submissionService).attachLibraryAssetTo(submission, assetId, moderator());
        ValidationLog entry = savedLog();
        assertThat(entry.getAction()).isEqualTo(ValidationAction.media_added);
        assertThat(entry.getEditSeverity()).isEqualTo("added_media");
        assertThat(entry.getRemarks()).isEqualTo("needed a wider crowd shot");
    }

    @Test
    void requestRevision_afterSessionEdit_firesEditedDuringReviewEvent() {
        Submission submission = inReviewSubmission();
        stubInReview(submission);
        ValidationLog lockLog = new ValidationLog();
        lockLog.setAction(ValidationAction.lock_acquired);
        ValidationLog editLog = new ValidationLog();
        editLog.setAction(ValidationAction.edited);
        editLog.setEditSeverity("flagged");
        when(validationLogRepository.findBySubmissionIdOrderByCreatedAtAsc(submission.getId()))
                .thenReturn(List.of(lockLog, editLog));

        validationService.requestRevision(submission.getId(), "Please tighten the caption and re-submit.", moderator());

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.atLeastOnce()).publishEvent(events.capture());
        assertThat(events.getAllValues())
                .anyMatch(e -> e instanceof com.dasigconnect.backend.event.SubmissionEditedDuringReviewEvent
                        && ((com.dasigconnect.backend.event.SubmissionEditedDuringReviewEvent) e).severity()
                        == com.dasigconnect.backend.model.entity.ReviewEditSeverity.FLAGGED);
    }

    @Test
    void getQueue_callsNetworkWideQueryRegardlessOfCallerInstitution() {
        JwtUserDetails admin = new JwtUserDetails(adminId, "admin@dasigconnect.local", "moderator", null);
        when(submissionRepository.findValidationQueue()).thenReturn(List.of());

        validationService.getQueue(admin);

        verify(submissionRepository).findValidationQueue();
    }

    @Test
    void getHistory_callsNetworkWideQueryRegardlessOfCallerInstitution() {
        JwtUserDetails admin = new JwtUserDetails(adminId, "admin@dasigconnect.local", "moderator", null);
        when(submissionRepository.findValidationHistory()).thenReturn(List.of());

        validationService.getHistory(admin);

        verify(submissionRepository).findValidationHistory();
    }

    @Test
    void getQueuePage_clampsPageSizeMapsCountsAndBatchesPageMedia() {
        Submission submission = inReviewSubmission();
        submission.setEventTitle("Research Expo");
        submission.setEventDate(java.time.LocalDate.parse("2026-10-01"));
        submission.getInstitution().setName("CIT-U");
        submission.getContributor().setEmail("contributor@example.test");

        when(submissionRepository.findValidationPage(
                any(), any(), any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(new PageImpl<>(
                        List.of(submission),
                        PageRequest.of(0, 50),
                        61));
        when(submissionMediaAssetRepository.findListPreviewMediaBySubmissionIds(any()))
                .thenReturn(List.of());

        SubmissionRepository.SubmissionStatusCount pending = mock(
                SubmissionRepository.SubmissionStatusCount.class);
        SubmissionRepository.SubmissionStatusCount published = mock(
                SubmissionRepository.SubmissionStatusCount.class);
        when(pending.getStatus()).thenReturn(SubmissionStatus.pending);
        when(pending.getCount()).thenReturn(4L);
        when(published.getStatus()).thenReturn(SubmissionStatus.published);
        when(published.getCount()).thenReturn(7L);
        when(submissionRepository.countValidationStatuses()).thenReturn(List.of(pending, published));

        var result = validationService.getQueuePage(
                moderator(), "in-review", "submitted", -3, 500, " Research ");

        assertThat(result.page()).isZero();
        assertThat(result.pageSize()).isEqualTo(50);
        assertThat(result.totalCount()).isEqualTo(61);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.items()).hasSize(1);
        assertThat(result.counts().all()).isEqualTo(11);
        assertThat(result.counts().pending()).isEqualTo(4);
        assertThat(result.counts().published()).isEqualTo(7);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SubmissionStatus>> statuses = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> search = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> sort = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> active = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Boolean> ascending = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<org.springframework.data.domain.Pageable> pageable =
                ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(submissionRepository).findValidationPage(
                statuses.capture(),
                search.capture(),
                sort.capture(),
                active.capture(),
                ascending.capture(),
                pageable.capture());
        assertThat(statuses.getValue()).containsExactly(SubmissionStatus.in_review);
        assertThat(search.getValue()).isEqualTo("research");
        assertThat(sort.getValue()).isEqualTo("submitted");
        assertThat(active.getValue()).isTrue();
        assertThat(ascending.getValue()).isTrue();
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        verify(submissionMediaAssetRepository)
                .findListPreviewMediaBySubmissionIds(List.of(submission.getId()));
        verify(submissionMediaAssetRepository, never()).countBySubmissionId(any());
    }

    @Test
    void getQueuePage_allUsesDescendingHistoryOrderingAndRejectsUnsupportedInputs() {
        when(submissionRepository.findValidationPage(
                any(), any(), any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(submissionRepository.countValidationStatuses()).thenReturn(List.of());

        validationService.getQueuePage(moderator(), "all", "publish_slot", 0, 20, "");

        verify(submissionRepository).findValidationPage(
                any(),
                any(),
                any(),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.eq(false),
                any());

        assertThatThrownBy(() ->
                validationService.getQueuePage(moderator(), "failed", "submitted", 0, 20, ""))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Unsupported validation queue view");
        assertThatThrownBy(() ->
                validationService.getQueuePage(moderator(), "all", "unknown", 0, 20, ""))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Unsupported validation queue sort");
    }

    @Test
    void getQueuePage_needsRevisionUsesFrozenSnapshotWithoutLiveMediaLookup() {
        Submission submission = inReviewSubmission();
        submission.setStatus(SubmissionStatus.needs_revision);
        submission.setEventTitle("Unsubmitted contributor edit");
        submission.setEventDate(java.time.LocalDate.parse("2026-12-31"));
        submission.getInstitution().setName("CIT-U");
        submission.getContributor().setEmail("contributor@example.test");
        submission.setReviewSnapshot("""
                {
                  "eventTitle": "Frozen reviewed title",
                  "eventDate": "2026-10-01",
                  "caption": "Frozen caption",
                  "fastTrack": false,
                  "tags": [],
                  "mediaTags": [],
                  "scheduledAt": "2026-10-02T02:00:00Z",
                  "mediaCount": 2,
                  "mediaAssets": []
                }
                """);

        when(submissionRepository.findValidationPage(
                any(), any(), any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(new PageImpl<>(List.of(submission), PageRequest.of(0, 20), 1));
        when(submissionRepository.countValidationStatuses()).thenReturn(List.of());

        var result = validationService.getQueuePage(
                moderator(), "needs_revision", "publish_slot", 0, 20, "");

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.getEventTitle()).isEqualTo("Frozen reviewed title");
            assertThat(item.getCaption()).isEqualTo("Frozen caption");
            assertThat(item.getMediaCount()).isEqualTo(2);
        });
        verify(submissionMediaAssetRepository, never())
                .findListPreviewMediaBySubmissionIds(any());
        verify(submissionMediaAssetRepository, never()).countBySubmissionId(any());
    }

    @Test
    void approve_moderatorCanActOnSubmissionFromAnyInstitution() {
        // Moderator accounts are network-wide (institutionId is always null),
        // so a submission belonging to a different institution than the caller's
        // must still be reviewable — this used to 404 before the scoping fix.
        JwtUserDetails admin = new JwtUserDetails(adminId, "admin@dasigconnect.local", "moderator", null);

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
    void approve_selfReview_isBlockedAndLeftForAnotherModerator() {
        UUID sharedId = UUID.randomUUID();
        JwtUserDetails admin = new JwtUserDetails(sharedId, "admin@dasigconnect.local", "moderator", null);

        Submission submission = new Submission();
        submission.setId(UUID.randomUUID());
        submission.setStatus(SubmissionStatus.pending);

        Institution institution = new Institution();
        institution.setId(submissionInstitutionId);
        submission.setInstitution(institution);

        User contributor = new User();
        contributor.setId(sharedId); // same identity as the reviewing admin
        submission.setContributor(contributor);

        when(submissionRepository.findById(submission.getId())).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> validationService.approve(submission.getId(), admin))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("reviewed by another moderator");

        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.pending);
        verify(slotReservationService, never()).confirm(any());
        verify(submissionRepository, never()).save(any());
        verify(validationLogRepository, never()).save(any());
    }

    @Test
    void approve_fastTrackSubmission_skipsSlotConfirmationAndFlagsAuditLog() {
        // Fast-Track (Live Event) submissions never get a slot reservation created
        // (see SubmissionService.create()), so confirming one would throw
        // IllegalStateException and roll back the whole approval. Approve must skip
        // slot confirmation for fast-track submissions and flag the audit log.
        JwtUserDetails admin = new JwtUserDetails(adminId, "admin@dasigconnect.local", "moderator", null);

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
        JwtUserDetails admin = new JwtUserDetails(adminId, "admin@dasigconnect.local", "moderator", null);

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
    void approve_standardSubmission_snapshotsOriginalScheduledAt() {
        // UC-3.1: the Moderator reschedule cap anchors its 1-day window to
        // originalScheduledAt, captured once here and never touched again.
        JwtUserDetails admin = new JwtUserDetails(adminId, "admin@dasigconnect.local", "moderator", null);

        Submission submission = new Submission();
        submission.setId(UUID.randomUUID());
        submission.setStatus(SubmissionStatus.pending);
        submission.setFastTrack(false);
        java.time.Instant slot = java.time.Instant.parse("2026-06-01T08:00:00Z");
        submission.setScheduledAt(slot);

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

        assertThat(submission.getOriginalScheduledAt()).isEqualTo(slot);
    }

    @Test
    void edit_keepsSubmissionInReviewAndLogsStandaloneEditedAction() {
        // A9: a standalone edit records its diff but does NOT transition the
        // submission out of IN_REVIEW and never confirms a slot or fires approval.
        JwtUserDetails admin = new JwtUserDetails(adminId, "admin@dasigconnect.local", "moderator", null);

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
        when(submissionService.applySubmissionEdits(any(), any(), any())).thenAnswer(invocation -> {
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
        JwtUserDetails admin = new JwtUserDetails(adminId, "admin@dasigconnect.local", "moderator", null);
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
        JwtUserDetails admin = new JwtUserDetails(adminId, "admin@dasigconnect.local", "moderator", null);
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
        JwtUserDetails admin = new JwtUserDetails(adminId, "admin@dasigconnect.local", "moderator", null);

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
        verify(eventPublisher, org.mockito.Mockito.atLeastOnce()).publishEvent(eventCaptor.capture());

        com.dasigconnect.backend.event.SubmissionApprovedEvent approvedEvent = eventCaptor.getAllValues().stream()
                .filter(e -> e instanceof com.dasigconnect.backend.event.SubmissionApprovedEvent)
                .map(e -> (com.dasigconnect.backend.event.SubmissionApprovedEvent) e)
                .findFirst().orElseThrow();
        assertThat(approvedEvent.edited()).isTrue();

        assertThat(eventCaptor.getAllValues())
                .anyMatch(e -> e instanceof com.dasigconnect.backend.event.SubmissionEditedDuringReviewEvent);
    }

    @Test
    void approve_editMadeInAnEarlierLockSession_stillCountsAsEdited() {
        // Regression: a Moderator edits, then releases/loses the lock (interrupted,
        // TTL expiry) before approving, and later reacquires it to finish the
        // review with no further edits. The earlier edit — and its diff — must
        // still be reflected in the approval, because `logsSinceLock` scopes to
        // the whole review cycle (since the last terminal action), not just the
        // most recent `lock_acquired` row. Previously this silently dropped the
        // edit: `edited` came back false and the contributor was never told.
        JwtUserDetails admin = new JwtUserDetails(adminId, "admin@dasigconnect.local", "moderator", null);

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

        ValidationLog firstLock = new ValidationLog();
        firstLock.setAction(ValidationAction.lock_acquired);
        ValidationLog editLog = new ValidationLog();
        editLog.setAction(ValidationAction.edited);
        editLog.setEditDiff("{\"caption\":{\"from\":\"old\",\"to\":\"new\"}}");
        ValidationLog released = new ValidationLog();
        released.setAction(ValidationAction.lock_released);
        ValidationLog secondLock = new ValidationLog();
        secondLock.setAction(ValidationAction.lock_acquired);

        when(submissionRepository.findById(submission.getId())).thenReturn(Optional.of(submission));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(validationLogRepository.findBySubmissionIdOrderByCreatedAtAsc(submission.getId()))
                .thenReturn(List.of(firstLock, editLog, released, secondLock));

        validationService.approve(submission.getId(), admin);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.atLeastOnce()).publishEvent(eventCaptor.capture());

        com.dasigconnect.backend.event.SubmissionApprovedEvent approvedEvent = eventCaptor.getAllValues().stream()
                .filter(e -> e instanceof com.dasigconnect.backend.event.SubmissionApprovedEvent)
                .map(e -> (com.dasigconnect.backend.event.SubmissionApprovedEvent) e)
                .findFirst().orElseThrow();
        assertThat(approvedEvent.edited()).isTrue();

        ArgumentCaptor<ValidationLog> logCaptor = ArgumentCaptor.forClass(ValidationLog.class);
        verify(validationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getEditDiff()).contains("caption").contains("old").contains("new");
    }

    @Test
    void approve_editFromAPriorCompletedReviewCycle_isNotCountedAgain() {
        // The flip side: once a review cycle ends in a terminal action, an edit
        // from before that boundary must NOT bleed into a later cycle's diff
        // (e.g. after Request Revision -> contributor resubmits -> re-reviewed).
        JwtUserDetails admin = new JwtUserDetails(adminId, "admin@dasigconnect.local", "moderator", null);

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

        ValidationLog oldLock = new ValidationLog();
        oldLock.setAction(ValidationAction.lock_acquired);
        ValidationLog oldEdit = new ValidationLog();
        oldEdit.setAction(ValidationAction.edited);
        oldEdit.setEditDiff("{\"caption\":{\"from\":\"a\",\"to\":\"b\"}}");
        ValidationLog priorRevision = new ValidationLog();
        priorRevision.setAction(ValidationAction.needs_revision);
        ValidationLog newLock = new ValidationLog();
        newLock.setAction(ValidationAction.lock_acquired);

        when(submissionRepository.findById(submission.getId())).thenReturn(Optional.of(submission));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(validationLogRepository.findBySubmissionIdOrderByCreatedAtAsc(submission.getId()))
                .thenReturn(List.of(oldLock, oldEdit, priorRevision, newLock));

        validationService.approve(submission.getId(), admin);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.atLeastOnce()).publishEvent(eventCaptor.capture());

        com.dasigconnect.backend.event.SubmissionApprovedEvent approvedEvent = eventCaptor.getAllValues().stream()
                .filter(e -> e instanceof com.dasigconnect.backend.event.SubmissionApprovedEvent)
                .map(e -> (com.dasigconnect.backend.event.SubmissionApprovedEvent) e)
                .findFirst().orElseThrow();
        assertThat(approvedEvent.edited()).isFalse();
    }
}
