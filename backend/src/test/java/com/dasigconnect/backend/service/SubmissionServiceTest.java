package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.guardrail.GuardRailResult;
import com.dasigconnect.backend.model.dto.guardrail.GuardRailViolation;
import com.dasigconnect.backend.model.dto.submission.AttachAssetDto;
import com.dasigconnect.backend.model.dto.submission.AttachMediaDto;
import com.dasigconnect.backend.model.dto.submission.SlotEvaluateRequestDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionCreateDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionMediaOrderDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionResponseDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionSummaryDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionUpdateDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionMediaAsset;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.model.entity.UserStatus;
import com.dasigconnect.backend.model.entity.NotificationEventType;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubmissionServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private MediaAssetRepository mediaAssetRepository;

    @Mock
    private SubmissionMediaAssetRepository submissionMediaAssetRepository;

    @Mock
    private SlotReservationService slotReservationService;

    @Mock
    private GuardRailService guardRailService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private SubmissionService submissionService;

    private UUID contributorId;
    private UUID institutionId;
    private User contributor;
    private Institution institution;
    private JwtUserDetails contributorPrincipal;

    @BeforeEach
    void setUp() {
        contributorId = UUID.randomUUID();
        institutionId = UUID.randomUUID();
        institution = institution(institutionId);
        contributor = user(contributorId, "contributor@cit.edu.ph", UserRole.contributor, institution);
        contributorPrincipal = principal(contributorId, "contributor", institutionId);

        when(userRepository.findByInstitutionIdAndRoleOrderByCreatedAtDesc(institutionId, UserRole.validator))
                .thenReturn(List.of());

        ReflectionTestUtils.setField(submissionService, "entityManager", entityManager);
        ReflectionTestUtils.setField(submissionService, "guardRailsEnforced", true);
    }

    @Test
    void create_withScheduledSlot_savesDraftReservesSlotAndAudits() {
        Instant scheduledAt = Instant.parse("2026-06-01T08:00:00Z");
        SubmissionCreateDto dto = createDto(scheduledAt);
        when(entityManager.getReference(User.class, contributorId)).thenReturn(contributor);
        when(entityManager.getReference(Institution.class, institutionId)).thenReturn(institution);
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> assignSubmissionId(invocation.getArgument(0)));
        when(submissionMediaAssetRepository.findBySubmissionIdOrderByDisplayOrderAsc(any())).thenReturn(List.of());

        SubmissionResponseDto result = submissionService.create(dto, contributorPrincipal);

        assertThat(result.getStatus()).isEqualTo("draft");
        assertThat(result.getEventTitle()).isEqualTo("Research Expo");
        assertThat(result.getScheduledAt()).isEqualTo(scheduledAt);
        verify(slotReservationService).reserve(result.getId(), institutionId, scheduledAt);
        verify(auditLogService).record(eq(contributor), eq("SUBMISSION_CREATED"), eq(null), eq(null), eq(result.getId()), any());
    }

    @Test
    void update_draftSubmission_updatesFieldsAndReservesChangedSlot() {
        UUID submissionId = UUID.randomUUID();
        Instant oldSlot = Instant.parse("2026-06-01T08:00:00Z");
        Instant newSlot = Instant.parse("2026-06-02T08:00:00Z");
        Submission submission = submission(submissionId, SubmissionStatus.draft, oldSlot);
        SubmissionUpdateDto dto = new SubmissionUpdateDto();
        dto.setEventTitle("Updated Title");
        dto.setScheduledAt(newSlot);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionRepository.save(submission)).thenReturn(submission);
        when(submissionMediaAssetRepository.findBySubmissionIdOrderByDisplayOrderAsc(submissionId)).thenReturn(List.of());

        SubmissionResponseDto result = submissionService.update(submissionId, dto, contributorPrincipal);

        assertThat(result.getEventTitle()).isEqualTo("Updated Title");
        assertThat(result.getScheduledAt()).isEqualTo(newSlot);
        verify(slotReservationService).reserve(submissionId, institutionId, newSlot);
    }

    @Test
    void update_pendingSubmission_isRejected() {
        UUID submissionId = UUID.randomUUID();
        when(submissionRepository.findById(submissionId))
                .thenReturn(Optional.of(submission(submissionId, SubmissionStatus.pending, Instant.now())));

        assertThatThrownBy(() -> submissionService.update(submissionId, new SubmissionUpdateDto(), contributorPrincipal))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void delete_draftSubmission_releasesSlotAndDeletes() {
        UUID submissionId = UUID.randomUUID();
        Submission submission = submission(submissionId, SubmissionStatus.draft, Instant.now());
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        submissionService.delete(submissionId, contributorPrincipal);

        verify(slotReservationService).deleteAllForSubmission(submissionId);
        verify(submissionRepository).delete(submission);
    }

    @Test
    void submit_draftWithCleanSlot_transitionsToPendingAndAudits() {
        UUID submissionId = UUID.randomUUID();
        Instant scheduledAt = Instant.parse("2026-06-01T08:00:00Z");
        Submission submission = submission(submissionId, SubmissionStatus.draft, scheduledAt);
        User validator = user(UUID.randomUUID(), "validator@cit.edu.ph", UserRole.validator, institution);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(guardRailService.validate(institutionId, scheduledAt)).thenReturn(new GuardRailResult());
        when(submissionRepository.save(submission)).thenReturn(submission);
        when(entityManager.getReference(User.class, contributorId)).thenReturn(contributor);
        when(submissionMediaAssetRepository.countBySubmissionId(submissionId)).thenReturn(1L);
        when(submissionMediaAssetRepository.findBySubmissionIdOrderByDisplayOrderAsc(submissionId)).thenReturn(List.of());
        when(userRepository.findByInstitutionIdAndRoleOrderByCreatedAtDesc(institutionId, UserRole.validator))
                .thenReturn(List.of(validator));

        SubmissionResponseDto result = submissionService.submit(submissionId, contributorPrincipal);

        assertThat(result.getStatus()).isEqualTo("pending");
        assertThat(result.getSubmittedAt()).isNotNull();
        verify(auditLogService).record(eq(contributor), eq("SUBMISSION_SUBMITTED"), eq(null), eq(null), eq(submissionId), any());
        // T1 — contributor does NOT receive a notification; validators receive the spec message
        verify(notificationService, never()).createNotification(eq(contributor), any(), any(), any());
        verify(notificationService).createNotification(
                eq(validator),
                eq(NotificationEventType.submission_pending),
                org.mockito.ArgumentMatchers.contains("submitted 'Research Expo' for review"),
                eq("/submissions/" + submissionId));
    }

    @Test
    void submit_withoutScheduledAt_returns400() {
        UUID submissionId = UUID.randomUUID();
        when(submissionRepository.findById(submissionId))
                .thenReturn(Optional.of(submission(submissionId, SubmissionStatus.draft, null)));

        assertThatThrownBy(() -> submissionService.submit(submissionId, contributorPrincipal))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(guardRailService, never()).validate(any(), any());
    }

    @Test
    void submit_blockedGuardRail_returns409() {
        UUID submissionId = UUID.randomUUID();
        Instant scheduledAt = Instant.parse("2026-06-01T08:00:00Z");
        when(submissionRepository.findById(submissionId))
                .thenReturn(Optional.of(submission(submissionId, SubmissionStatus.draft, scheduledAt)));
        GuardRailViolation violation = new GuardRailViolation("GR-H1", "Slot already taken");
        when(guardRailService.validate(institutionId, scheduledAt))
                .thenReturn(new GuardRailResult(List.of(violation), List.of()));

        assertThatThrownBy(() -> submissionService.submit(submissionId, contributorPrincipal))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void submit_blockedGuardRail_whenEnforcementDisabled_transitionsToPending() {
        ReflectionTestUtils.setField(submissionService, "guardRailsEnforced", false);
        UUID submissionId = UUID.randomUUID();
        Instant scheduledAt = Instant.parse("2026-06-01T08:00:00Z");
        Submission submission = submission(submissionId, SubmissionStatus.draft, scheduledAt);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionRepository.save(submission)).thenReturn(submission);
        when(entityManager.getReference(User.class, contributorId)).thenReturn(contributor);
        when(submissionMediaAssetRepository.countBySubmissionId(submissionId)).thenReturn(1L);
        when(submissionMediaAssetRepository.findBySubmissionIdOrderByDisplayOrderAsc(submissionId)).thenReturn(List.of());

        SubmissionResponseDto result = submissionService.submit(submissionId, contributorPrincipal);

        assertThat(result.getStatus()).isEqualTo("pending");
        verify(guardRailService, never()).validate(any(), any());
    }

    @Test
    void submit_withoutMedia_returns422() {
        UUID submissionId = UUID.randomUUID();
        Instant scheduledAt = Instant.parse("2026-06-01T08:00:00Z");
        Submission submission = submission(submissionId, SubmissionStatus.draft, scheduledAt);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(guardRailService.validate(institutionId, scheduledAt)).thenReturn(new GuardRailResult());
        when(submissionMediaAssetRepository.countBySubmissionId(submissionId)).thenReturn(0L);

        assertThatThrownBy(() -> submissionService.submit(submissionId, contributorPrincipal))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .extracting(status -> status.value())
                .isEqualTo(422);
        verify(submissionRepository, never()).save(submission);
    }

    @Test
    void submit_withoutCaption_returns422() {
        UUID submissionId = UUID.randomUUID();
        Instant scheduledAt = Instant.parse("2026-06-01T08:00:00Z");
        Submission submission = submission(submissionId, SubmissionStatus.draft, scheduledAt);
        submission.setCaption("   ");
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(guardRailService.validate(institutionId, scheduledAt)).thenReturn(new GuardRailResult());
        when(submissionMediaAssetRepository.countBySubmissionId(submissionId)).thenReturn(2L);

        assertThatThrownBy(() -> submissionService.submit(submissionId, contributorPrincipal))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .extracting(status -> status.value())
                .isEqualTo(422);
        verify(submissionRepository, never()).save(submission);
    }

    @Test
    void list_contributorUsesContributorScopedQueryAndAddsMediaCount() {
        Submission submission = submission(UUID.randomUUID(), SubmissionStatus.draft, Instant.now());
        when(submissionRepository.findByContributorIdAndInstitutionIdOrderByCreatedAtDesc(contributorId, institutionId))
                .thenReturn(List.of(submission));
        when(submissionMediaAssetRepository.countBySubmissionId(submission.getId())).thenReturn(3L);

        List<SubmissionSummaryDto> result = submissionService.list(contributorPrincipal);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMediaCount()).isEqualTo(3L);
        verify(submissionRepository).findByContributorIdAndInstitutionIdOrderByCreatedAtDesc(contributorId, institutionId);
    }

    @Test
    void get_validatorFromOtherInstitutionIsForbidden() {
        Submission submission = submission(UUID.randomUUID(), SubmissionStatus.pending, Instant.now());
        when(submissionRepository.findById(submission.getId())).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> submissionService.get(
                submission.getId(),
                principal(UUID.randomUUID(), "validator", UUID.randomUUID())))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void evaluateSlot_delegatesToGuardRailServiceWithTenantInstitution() {
        Instant scheduledAt = Instant.parse("2026-06-01T08:00:00Z");
        SlotEvaluateRequestDto dto = new SlotEvaluateRequestDto();
        dto.setScheduledAt(scheduledAt);
        GuardRailResult expected = new GuardRailResult();
        when(guardRailService.validate(institutionId, scheduledAt)).thenReturn(expected);

        GuardRailResult result = submissionService.evaluateSlot(dto, contributorPrincipal);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void attachMedia_createsMediaAssetAndLink() {
        UUID submissionId = UUID.randomUUID();
        Submission submission = submission(submissionId, SubmissionStatus.draft, Instant.now());
        AttachMediaDto dto = new AttachMediaDto();
        dto.setStorageUrl("https://storage.example/media/photo.jpg");
        dto.setFileName("photo.jpg");
        dto.setFileType("JPEG");
        dto.setFileSizeBytes(1024L);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionMediaAssetRepository.countBySubmissionId(submissionId)).thenReturn(0L);
        when(entityManager.getReference(Institution.class, institutionId)).thenReturn(institution);
        when(entityManager.getReference(User.class, contributorId)).thenReturn(contributor);
        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(invocation -> assignMediaAssetId(invocation.getArgument(0)));
        when(submissionMediaAssetRepository.findBySubmissionIdOrderByDisplayOrderAsc(submissionId)).thenReturn(List.of());

        SubmissionResponseDto result = submissionService.attachMedia(submissionId, dto, contributorPrincipal);

        assertThat(result.getId()).isEqualTo(submissionId);
        verify(mediaAssetRepository).save(any(MediaAsset.class));
        verify(submissionMediaAssetRepository).save(any(SubmissionMediaAsset.class));
    }

    @Test
    void attachMedia_whenSubmissionAlreadyHasTenAssets_returns422() {
        UUID submissionId = UUID.randomUUID();
        when(submissionRepository.findById(submissionId))
                .thenReturn(Optional.of(submission(submissionId, SubmissionStatus.draft, Instant.now())));
        when(submissionMediaAssetRepository.countBySubmissionId(submissionId)).thenReturn(10L);

        assertThatThrownBy(() -> submissionService.attachMedia(submissionId, new AttachMediaDto(), contributorPrincipal))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .extracting(status -> status.value())
                .isEqualTo(422);
    }

    @Test
    void attachAsset_rejectsAssetFromOtherInstitution() {
        UUID submissionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        AttachAssetDto dto = new AttachAssetDto();
        dto.setMediaAssetId(assetId);
        MediaAsset asset = mediaAsset(assetId, institution(UUID.randomUUID()));
        when(submissionRepository.findById(submissionId))
                .thenReturn(Optional.of(submission(submissionId, SubmissionStatus.draft, Instant.now())));
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> submissionService.attachAsset(submissionId, dto, contributorPrincipal))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void reorderMedia_updatesDisplayOrder() {
        UUID submissionId = UUID.randomUUID();
        UUID firstAssetId = UUID.randomUUID();
        UUID secondAssetId = UUID.randomUUID();
        Submission submission = submission(submissionId, SubmissionStatus.draft, Instant.now());
        SubmissionMediaAsset firstLink = mediaLink(submission, mediaAsset(firstAssetId, institution), 0);
        SubmissionMediaAsset secondLink = mediaLink(submission, mediaAsset(secondAssetId, institution), 1);
        SubmissionMediaOrderDto dto = new SubmissionMediaOrderDto();
        dto.setMediaAssetIds(List.of(secondAssetId, firstAssetId));

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionMediaAssetRepository.findBySubmissionIdOrderByDisplayOrderAsc(submissionId))
                .thenReturn(List.of(firstLink, secondLink))
                .thenReturn(List.of(secondLink, firstLink));

        SubmissionResponseDto result = submissionService.reorderMedia(submissionId, dto, contributorPrincipal);

        assertThat(result.getId()).isEqualTo(submissionId);
        assertThat(secondLink.getDisplayOrder()).isZero();
        assertThat(firstLink.getDisplayOrder()).isEqualTo(1);
        verify(submissionMediaAssetRepository).saveAll(List.of(firstLink, secondLink));
    }

    private SubmissionCreateDto createDto(Instant scheduledAt) {
        SubmissionCreateDto dto = new SubmissionCreateDto();
        dto.setEventTitle("Research Expo");
        dto.setEventDate(LocalDate.of(2026, 6, 1));
        dto.setCaption("Caption");
        dto.setDescription("Description");
        dto.setScheduledAt(scheduledAt);
        return dto;
    }

    private Submission submission(UUID id, SubmissionStatus status, Instant scheduledAt) {
        Submission submission = new Submission();
        submission.setId(id);
        submission.setContributor(contributor);
        submission.setInstitution(institution);
        submission.setEventTitle("Research Expo");
        submission.setEventDate(LocalDate.of(2026, 6, 1));
        submission.setCaption("Caption");
        submission.setDescription("Description");
        submission.setStatus(status);
        submission.setScheduledAt(scheduledAt);
        return submission;
    }

    private static Submission assignSubmissionId(Submission submission) {
        if (submission.getId() == null) {
            submission.setId(UUID.randomUUID());
        }
        return submission;
    }

    private static MediaAsset assignMediaAssetId(MediaAsset asset) {
        if (asset.getId() == null) {
            asset.setId(UUID.randomUUID());
        }
        return asset;
    }

    private MediaAsset mediaAsset(UUID id, Institution institution) {
        MediaAsset asset = new MediaAsset();
        asset.setId(id);
        asset.setInstitution(institution);
        asset.setUploader(contributor);
        asset.setAssetCode("ASSET-12345678");
        asset.setStorageUrl("https://storage.example/media/photo.jpg");
        asset.setFileName("photo.jpg");
        asset.setFileType(MediaFileType.jpeg);
        asset.setFileSizeBytes(1024L);
        return asset;
    }

    private static SubmissionMediaAsset mediaLink(
            Submission submission,
            MediaAsset mediaAsset,
            int displayOrder) {
        SubmissionMediaAsset link = new SubmissionMediaAsset();
        link.setSubmission(submission);
        link.setMediaAsset(mediaAsset);
        link.setDisplayOrder(displayOrder);
        return link;
    }

    private static Institution institution(UUID id) {
        Institution institution = new Institution();
        institution.setId(id);
        institution.setName("CIT-U");
        institution.setCode("CIT-U");
        institution.setEmailDomain("cit.edu.ph");
        return institution;
    }

    private static User user(UUID id, String email, UserRole role, Institution institution) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        user.setAccountState(UserStatus.active);
        user.setInstitution(institution);
        return user;
    }

    private static JwtUserDetails principal(UUID id, String role, UUID institutionId) {
        return new JwtUserDetails(id, role + "@example.com", role, institutionId);
    }
}
