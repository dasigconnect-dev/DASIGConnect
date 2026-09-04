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
import com.dasigconnect.backend.model.entity.MediaAlbum;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetStatus;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionMediaAsset;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.model.entity.UserStatus;
import com.dasigconnect.backend.model.entity.NotificationEventType;
import com.dasigconnect.backend.repository.InstitutionRepository;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubmissionServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private InstitutionRepository institutionRepository;

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
    private MediaStorageService mediaStorage;

    @Mock
    private EntityManager entityManager;

    @Mock
    private com.dasigconnect.backend.repository.AssetTagRepository assetTagRepository;

    @Mock
    private com.dasigconnect.backend.repository.MediaAlbumRepository mediaAlbumRepository;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

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

        when(userRepository.findByInstitutionIdAndRoleOrderByCreatedAtDesc(institutionId, UserRole.moderator))
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
    void create_byModeratorWithoutInstitutionId_defaultsToDasigCentralVisayas() {
        Instant scheduledAt = Instant.parse("2026-06-01T08:00:00Z");
        SubmissionCreateDto dto = createDto(scheduledAt);
        dto.setInstitutionId(null); // Omitted institution_id

        UUID adminId = UUID.randomUUID();
        User admin = user(adminId, "admin@dasigconnect.com", UserRole.admin, null);
        JwtUserDetails adminPrincipal = principal(adminId, "admin", null);

        UUID dasigInstId = UUID.randomUUID();
        Institution dasigInst = new Institution();
        dasigInst.setId(dasigInstId);
        dasigInst.setName("DASIG Central Visayas");
        dasigInst.setProtected(true);

        when(institutionRepository.findByNameIgnoreCase("DASIG Central Visayas")).thenReturn(Optional.of(dasigInst));
        when(entityManager.getReference(User.class, adminId)).thenReturn(admin);
        when(entityManager.getReference(Institution.class, dasigInstId)).thenReturn(dasigInst);
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> assignSubmissionId(invocation.getArgument(0)));
        when(submissionMediaAssetRepository.findBySubmissionIdOrderByDisplayOrderAsc(any())).thenReturn(List.of());

        SubmissionResponseDto result = submissionService.create(dto, adminPrincipal);

        assertThat(result.getStatus()).isEqualTo("draft");
        verify(slotReservationService).reserve(result.getId(), dasigInstId, scheduledAt);
    }

    @Test
    void create_byContributorWithCustomInstitutionId_ignoresPayloadAndUsesSessionInstitutionId() {
        Instant scheduledAt = Instant.parse("2026-06-01T08:00:00Z");
        SubmissionCreateDto dto = createDto(scheduledAt);
        dto.setInstitutionId(UUID.randomUUID()); // Client supplied spoofed institutionId

        when(entityManager.getReference(User.class, contributorId)).thenReturn(contributor);
        when(entityManager.getReference(Institution.class, institutionId)).thenReturn(institution);
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> assignSubmissionId(invocation.getArgument(0)));
        when(submissionMediaAssetRepository.findBySubmissionIdOrderByDisplayOrderAsc(any())).thenReturn(List.of());

        SubmissionResponseDto result = submissionService.create(dto, contributorPrincipal);

        assertThat(result.getStatus()).isEqualTo("draft");
        // Verify slot reservation uses the contributor's session institutionId, NOT the DTO's institutionId
        verify(slotReservationService).reserve(result.getId(), institutionId, scheduledAt);
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
        when(guardRailService.validate(any(), any(), any())).thenReturn(new GuardRailResult(List.of(), List.of()));

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
        User validator = user(UUID.randomUUID(), "validator@cit.edu.ph", UserRole.moderator, institution);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(guardRailService.validate(eq(institutionId), eq(scheduledAt), any())).thenReturn(new GuardRailResult());
        when(submissionRepository.save(submission)).thenReturn(submission);
        when(entityManager.getReference(User.class, contributorId)).thenReturn(contributor);
        when(submissionMediaAssetRepository.countBySubmissionId(submissionId)).thenReturn(1L);
        when(submissionMediaAssetRepository.findBySubmissionIdOrderByDisplayOrderAsc(submissionId)).thenReturn(List.of());
        when(userRepository.findByRole(UserRole.moderator))
                .thenReturn(List.of(validator));

        SubmissionResponseDto result = submissionService.submit(submissionId, contributorPrincipal);

        assertThat(result.getStatus()).isEqualTo("pending");
        assertThat(result.getSubmittedAt()).isNotNull();
        verify(auditLogService).record(eq(contributor), eq("SUBMISSION_SUBMITTED"), eq(null), eq(null), eq(submissionId), any());
        // T1 — event is published for asynchronous multi-channel notification delivery (T-01)
        verify(eventPublisher).publishEvent(any(com.dasigconnect.backend.event.SubmissionPendingEvent.class));
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
        verify(guardRailService, never()).validate(any(), any(), any());
    }

    @Test
    void submit_blockedGuardRail_returns409() {
        UUID submissionId = UUID.randomUUID();
        Instant scheduledAt = Instant.parse("2026-06-01T08:00:00Z");
        when(submissionRepository.findById(submissionId))
                .thenReturn(Optional.of(submission(submissionId, SubmissionStatus.draft, scheduledAt)));
        GuardRailViolation violation = new GuardRailViolation("GR-H1", "Slot already taken");
        when(guardRailService.validate(eq(institutionId), eq(scheduledAt), any()))
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
        verify(guardRailService, never()).validate(any(), any(), any());
    }

    @Test
    void submit_withoutCaption_returns422() {
        UUID submissionId = UUID.randomUUID();
        Instant scheduledAt = Instant.parse("2026-06-01T08:00:00Z");
        Submission submission = submission(submissionId, SubmissionStatus.draft, scheduledAt);
        submission.setCaption("   ");
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(guardRailService.validate(eq(institutionId), eq(scheduledAt), any())).thenReturn(new GuardRailResult());
        when(submissionMediaAssetRepository.countBySubmissionId(submissionId)).thenReturn(2L);

        assertThatThrownBy(() -> submissionService.submit(submissionId, contributorPrincipal))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .extracting(status -> status.value())
                .isEqualTo(422);
        verify(submissionRepository, never()).save(submission);
    }

    @Test
    void submit_withoutMedia_returns422() {
        UUID submissionId = UUID.randomUUID();
        Instant scheduledAt = Instant.parse("2026-06-01T08:00:00Z");
        Submission submission = submission(submissionId, SubmissionStatus.draft, scheduledAt);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(guardRailService.validate(eq(institutionId), eq(scheduledAt), any())).thenReturn(new GuardRailResult());
        when(submissionMediaAssetRepository.countBySubmissionId(submissionId)).thenReturn(0L);

        assertThatThrownBy(() -> submissionService.submit(submissionId, contributorPrincipal))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("media attachment")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(422);
    }

    @Test
    void list_scopesToCallerAsAuthorAndAddsMediaCount() {
        Submission submission = submission(UUID.randomUUID(), SubmissionStatus.draft, Instant.now());
        when(submissionRepository.findByContributorIdOrderByCreatedAtDesc(contributorId))
                .thenReturn(List.of(submission));
        when(submissionMediaAssetRepository.countBySubmissionId(submission.getId())).thenReturn(3L);

        List<SubmissionSummaryDto> result = submissionService.list(contributorPrincipal);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMediaCount()).isEqualTo(3L);
        verify(submissionRepository).findByContributorIdOrderByCreatedAtDesc(contributorId);
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
        UUID submissionId = UUID.randomUUID();
        Instant scheduledAt = Instant.parse("2026-06-01T08:00:00Z");
        Submission submission = submission(submissionId, SubmissionStatus.draft, scheduledAt);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        SlotEvaluateRequestDto dto = new SlotEvaluateRequestDto();
        dto.setScheduledAt(scheduledAt);
        GuardRailResult expected = new GuardRailResult();
        when(guardRailService.validate(eq(institutionId), eq(scheduledAt), any())).thenReturn(expected);

        GuardRailResult result = submissionService.evaluateSlot(submissionId, dto, contributorPrincipal);

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
    void attachMedia_whenFileExceedsFiftyMb_returns422() {
        UUID submissionId = UUID.randomUUID();
        AttachMediaDto dto = new AttachMediaDto();
        dto.setStorageUrl("https://storage.example/media/video.mp4");
        dto.setFileName("video.mp4");
        dto.setFileType("mp4");
        dto.setFileSizeBytes(50L * 1024 * 1024 + 1);
        when(submissionRepository.findById(submissionId))
                .thenReturn(Optional.of(submission(submissionId, SubmissionStatus.draft, Instant.now())));

        assertThatThrownBy(() -> submissionService.attachMedia(submissionId, dto, contributorPrincipal))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("50 MB")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(422);
        verify(mediaAssetRepository, never()).save(any());
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
    void attachAsset_moderatorCanUseAssetFromOtherInstitution() {
        UUID moderatorId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        JwtUserDetails moderatorPrincipal = principal(moderatorId, "moderator", null);
        User moderator = user(moderatorId, "moderator@example.com", UserRole.moderator, null);
        Submission submission = submission(submissionId, SubmissionStatus.draft, Instant.now());
        submission.setContributor(moderator);
        MediaAsset asset = mediaAsset(assetId, institution(UUID.randomUUID()));
        AttachAssetDto dto = new AttachAssetDto();
        dto.setMediaAssetId(assetId);

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset));
        when(submissionMediaAssetRepository.existsBySubmissionIdAndMediaAssetId(submissionId, assetId)).thenReturn(false);
        when(submissionMediaAssetRepository.countBySubmissionId(submissionId)).thenReturn(0L);
        when(submissionMediaAssetRepository.findMediaAssetsBySubmissionId(submissionId)).thenReturn(List.of(asset));
        when(submissionMediaAssetRepository.findBySubmissionIdOrderByDisplayOrderAsc(submissionId)).thenReturn(List.of());

        submissionService.attachAsset(submissionId, dto, moderatorPrincipal);

        verify(submissionMediaAssetRepository).save(any(SubmissionMediaAsset.class));
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

    @Test
    void detachAsset_permanentlyDeletesAssetThatIsNowUnattachedAndWasNeverBeyondDraft() {
        UUID submissionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        Submission submission = submission(submissionId, SubmissionStatus.draft, Instant.now());
        MediaAsset asset = mediaAsset(assetId, institution);
        SubmissionMediaAsset link = mediaLink(submission, asset, 0);

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionMediaAssetRepository.findBySubmissionIdAndMediaAssetId(submissionId, assetId))
                .thenReturn(Optional.of(link));
        when(submissionMediaAssetRepository.findAssetIdsUsedBeyondDraft(List.of(assetId)))
                .thenReturn(java.util.Set.of());
        when(submissionMediaAssetRepository.existsByMediaAssetId(assetId)).thenReturn(false);
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset));
        when(mediaStorage.deletePublicObject(asset.getStorageUrl())).thenReturn(true);

        submissionService.detachAsset(submissionId, assetId, contributorPrincipal);

        verify(submissionMediaAssetRepository).delete(link);
        verify(mediaAssetRepository).delete(asset);
        verify(mediaAssetRepository, never()).save(asset);
        verify(mediaStorage).deletePublicObject(asset.getStorageUrl());
    }

    @Test
    void detachAsset_keepsAssetThatWasUsedBeyondDraft() {
        UUID submissionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        Submission submission = submission(submissionId, SubmissionStatus.draft, Instant.now());
        MediaAsset asset = mediaAsset(assetId, institution);
        SubmissionMediaAsset link = mediaLink(submission, asset, 0);

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionMediaAssetRepository.findBySubmissionIdAndMediaAssetId(submissionId, assetId))
                .thenReturn(Optional.of(link));
        when(submissionMediaAssetRepository.findAssetIdsUsedBeyondDraft(List.of(assetId)))
                .thenReturn(java.util.Set.of(assetId));

        submissionService.detachAsset(submissionId, assetId, contributorPrincipal);

        verify(submissionMediaAssetRepository).delete(link);
        verify(mediaAssetRepository, never()).save(any());
    }

    @Test
    void detachAsset_keepsAssetThatIsStillAttachedElsewhere() {
        UUID submissionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        Submission submission = submission(submissionId, SubmissionStatus.draft, Instant.now());
        MediaAsset asset = mediaAsset(assetId, institution);
        SubmissionMediaAsset link = mediaLink(submission, asset, 0);

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionMediaAssetRepository.findBySubmissionIdAndMediaAssetId(submissionId, assetId))
                .thenReturn(Optional.of(link));
        when(submissionMediaAssetRepository.findAssetIdsUsedBeyondDraft(List.of(assetId)))
                .thenReturn(java.util.Set.of());
        when(submissionMediaAssetRepository.existsByMediaAssetId(assetId)).thenReturn(true);

        submissionService.detachAsset(submissionId, assetId, contributorPrincipal);

        verify(submissionMediaAssetRepository).delete(link);
        verify(mediaAssetRepository, never()).save(any());
    }

    @Test
    void attachMedia_onDraft_stagesAssetWithNullInstitution() {
        UUID submissionId = UUID.randomUUID();
        Submission submission = submission(submissionId, SubmissionStatus.draft, Instant.now());
        AttachMediaDto dto = new AttachMediaDto();
        dto.setStorageUrl("https://storage.example/media/photo.jpg");
        dto.setFileName("photo.jpg");
        dto.setFileType("JPEG");
        dto.setFileSizeBytes(1024L);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionMediaAssetRepository.countBySubmissionId(submissionId)).thenReturn(0L);
        when(entityManager.getReference(User.class, contributorId)).thenReturn(contributor);
        when(mediaAssetRepository.save(any(MediaAsset.class)))
                .thenAnswer(invocation -> assignMediaAssetId(invocation.getArgument(0)));
        when(submissionMediaAssetRepository.findBySubmissionIdOrderByDisplayOrderAsc(submissionId)).thenReturn(List.of());

        submissionService.attachMedia(submissionId, dto, contributorPrincipal);

        org.mockito.ArgumentCaptor<MediaAsset> captor = org.mockito.ArgumentCaptor.forClass(MediaAsset.class);
        verify(mediaAssetRepository).save(captor.capture());
        assertThat(captor.getValue().getInstitution()).isNull();
        assertThat(captor.getValue().getStatus()).isEqualTo(MediaAssetStatus.STAGED);
    }

    @Test
    void attachMedia_copiesSubmissionMediaTagsOntoAssetAsManualTags() {
        UUID submissionId = UUID.randomUUID();
        Submission submission = submission(submissionId, SubmissionStatus.draft, Instant.now());
        submission.setMediaTags("event, dost7");
        AttachMediaDto dto = new AttachMediaDto();
        dto.setStorageUrl("https://storage.example/media/photo.jpg");
        dto.setFileName("photo.jpg");
        dto.setFileType("JPEG");
        dto.setFileSizeBytes(1024L);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionMediaAssetRepository.countBySubmissionId(submissionId)).thenReturn(0L);
        when(entityManager.getReference(User.class, contributorId)).thenReturn(contributor);
        when(mediaAssetRepository.save(any(MediaAsset.class)))
                .thenAnswer(invocation -> assignMediaAssetId(invocation.getArgument(0)));
        when(submissionMediaAssetRepository.findBySubmissionIdOrderByDisplayOrderAsc(submissionId)).thenReturn(List.of());

        submissionService.attachMedia(submissionId, dto, contributorPrincipal);

        org.mockito.ArgumentCaptor<com.dasigconnect.backend.model.entity.AssetTag> tags =
                org.mockito.ArgumentCaptor.forClass(com.dasigconnect.backend.model.entity.AssetTag.class);
        verify(assetTagRepository, org.mockito.Mockito.times(2)).save(tags.capture());
        assertThat(tags.getAllValues()).extracting(com.dasigconnect.backend.model.entity.AssetTag::getLabel)
                .containsExactly("event", "dost7");
        assertThat(tags.getAllValues()).allSatisfy(t -> assertThat(t.getSource()).isEqualTo("manual"));
    }

    @Test
    void attachMedia_onNeedsRevision_bindsInstitutionImmediately() {
        UUID submissionId = UUID.randomUUID();
        Submission submission = submission(submissionId, SubmissionStatus.needs_revision, Instant.now());
        AttachMediaDto dto = new AttachMediaDto();
        dto.setStorageUrl("https://storage.example/media/photo.jpg");
        dto.setFileName("photo.jpg");
        dto.setFileType("JPEG");
        dto.setFileSizeBytes(1024L);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionMediaAssetRepository.countBySubmissionId(submissionId)).thenReturn(0L);
        when(entityManager.getReference(Institution.class, institutionId)).thenReturn(institution);
        when(entityManager.getReference(User.class, contributorId)).thenReturn(contributor);
        when(mediaAssetRepository.save(any(MediaAsset.class)))
                .thenAnswer(invocation -> assignMediaAssetId(invocation.getArgument(0)));
        when(submissionMediaAssetRepository.findBySubmissionIdOrderByDisplayOrderAsc(submissionId)).thenReturn(List.of());

        submissionService.attachMedia(submissionId, dto, contributorPrincipal);

        org.mockito.ArgumentCaptor<MediaAsset> captor = org.mockito.ArgumentCaptor.forClass(MediaAsset.class);
        verify(mediaAssetRepository).save(captor.capture());
        assertThat(captor.getValue().getInstitution()).isEqualTo(institution);
        assertThat(captor.getValue().getStatus()).isNotEqualTo(MediaAssetStatus.STAGED);
    }

    @Test
    void attachMedia_onNeedsRevision_withAlbumName_resolvesThatAlbum() {
        UUID submissionId = UUID.randomUUID();
        Submission submission = submission(submissionId, SubmissionStatus.needs_revision, Instant.now());
        MediaAlbum target = new MediaAlbum();
        target.setId(UUID.randomUUID());
        target.setInstitution(institution);
        AttachMediaDto dto = new AttachMediaDto();
        dto.setStorageUrl("https://storage.example/media/photo.jpg");
        dto.setFileName("photo.jpg");
        dto.setFileType("JPEG");
        dto.setFileSizeBytes(1024L);
        dto.setAlbumName("Field Photos");
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionMediaAssetRepository.countBySubmissionId(submissionId)).thenReturn(0L);
        when(entityManager.getReference(Institution.class, institutionId)).thenReturn(institution);
        when(entityManager.getReference(User.class, contributorId)).thenReturn(contributor);
        when(mediaAlbumRepository.findByParentAndNameIgnoreCase(institutionId, null, "Field Photos"))
                .thenReturn(Optional.of(target));
        when(mediaAssetRepository.save(any(MediaAsset.class)))
                .thenAnswer(invocation -> assignMediaAssetId(invocation.getArgument(0)));
        when(submissionMediaAssetRepository.findBySubmissionIdOrderByDisplayOrderAsc(submissionId)).thenReturn(List.of());

        submissionService.attachMedia(submissionId, dto, contributorPrincipal);

        org.mockito.ArgumentCaptor<MediaAsset> captor = org.mockito.ArgumentCaptor.forClass(MediaAsset.class);
        verify(mediaAssetRepository).save(captor.capture());
        assertThat(captor.getValue().getMediaAlbum()).isEqualTo(target);
    }

    @Test
    void attachMedia_onNeedsRevision_withUnknownAlbumName_createsIt() {
        UUID submissionId = UUID.randomUUID();
        Submission submission = submission(submissionId, SubmissionStatus.needs_revision, Instant.now());
        AttachMediaDto dto = new AttachMediaDto();
        dto.setStorageUrl("https://storage.example/media/photo.jpg");
        dto.setFileName("photo.jpg");
        dto.setFileType("JPEG");
        dto.setFileSizeBytes(1024L);
        dto.setAlbumName("Brand New Album");
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionMediaAssetRepository.countBySubmissionId(submissionId)).thenReturn(0L);
        when(entityManager.getReference(Institution.class, institutionId)).thenReturn(institution);
        when(entityManager.getReference(User.class, contributorId)).thenReturn(contributor);
        when(mediaAlbumRepository.findByParentAndNameIgnoreCase(institutionId, null, "Brand New Album"))
                .thenReturn(Optional.empty());
        when(mediaAlbumRepository.save(any(MediaAlbum.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mediaAssetRepository.save(any(MediaAsset.class)))
                .thenAnswer(invocation -> assignMediaAssetId(invocation.getArgument(0)));
        when(submissionMediaAssetRepository.findBySubmissionIdOrderByDisplayOrderAsc(submissionId)).thenReturn(List.of());

        submissionService.attachMedia(submissionId, dto, contributorPrincipal);

        org.mockito.ArgumentCaptor<MediaAlbum> album = org.mockito.ArgumentCaptor.forClass(MediaAlbum.class);
        verify(mediaAlbumRepository).save(album.capture());
        assertThat(album.getValue().getName()).isEqualTo("Brand New Album");
        assertThat(album.getValue().getInstitution()).isEqualTo(institution);
    }

    @Test
    void attachMedia_onNeedsRevision_withoutAlbumName_fallsBackToSubmissionAlbum() {
        UUID submissionId = UUID.randomUUID();
        Submission submission = submission(submissionId, SubmissionStatus.needs_revision, Instant.now());
        MediaAlbum submissionAlbum = new MediaAlbum();
        submissionAlbum.setId(UUID.randomUUID());
        submissionAlbum.setInstitution(institution);
        AttachMediaDto dto = new AttachMediaDto();
        dto.setStorageUrl("https://storage.example/media/photo.jpg");
        dto.setFileName("photo.jpg");
        dto.setFileType("JPEG");
        dto.setFileSizeBytes(1024L);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionMediaAssetRepository.countBySubmissionId(submissionId)).thenReturn(0L);
        when(entityManager.getReference(Institution.class, institutionId)).thenReturn(institution);
        when(entityManager.getReference(User.class, contributorId)).thenReturn(contributor);
        when(mediaAlbumRepository.findByParentAndNameIgnoreCase(institutionId, null, "Research Expo Album"))
                .thenReturn(Optional.of(submissionAlbum));
        when(mediaAssetRepository.save(any(MediaAsset.class)))
                .thenAnswer(invocation -> assignMediaAssetId(invocation.getArgument(0)));
        when(submissionMediaAssetRepository.findBySubmissionIdOrderByDisplayOrderAsc(submissionId)).thenReturn(List.of());

        submissionService.attachMedia(submissionId, dto, contributorPrincipal);

        org.mockito.ArgumentCaptor<MediaAsset> captor = org.mockito.ArgumentCaptor.forClass(MediaAsset.class);
        verify(mediaAssetRepository).save(captor.capture());
        assertThat(captor.getValue().getMediaAlbum()).isEqualTo(submissionAlbum);
    }

    @Test
    void submit_promotesStagedMediaToProcessingAndStampsInstitution() {
        UUID submissionId = UUID.randomUUID();
        Instant scheduledAt = Instant.parse("2026-06-01T08:00:00Z");
        Submission submission = submission(submissionId, SubmissionStatus.draft, scheduledAt);
        MediaAsset staged = new MediaAsset();
        staged.setId(UUID.randomUUID());
        staged.setStatus(MediaAssetStatus.STAGED);
        staged.setFileType(MediaFileType.jpeg);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(guardRailService.validate(eq(institutionId), eq(scheduledAt), any())).thenReturn(new GuardRailResult());
        when(submissionRepository.save(submission)).thenReturn(submission);
        when(entityManager.getReference(User.class, contributorId)).thenReturn(contributor);
        when(submissionMediaAssetRepository.countBySubmissionId(submissionId)).thenReturn(1L);
        when(submissionMediaAssetRepository.findMediaAssetsBySubmissionId(submissionId))
                .thenReturn(List.of(staged));
        when(submissionMediaAssetRepository.findBySubmissionIdOrderByDisplayOrderAsc(submissionId)).thenReturn(List.of());
        when(userRepository.findByRole(UserRole.moderator)).thenReturn(List.of());

        submissionService.submit(submissionId, contributorPrincipal);

        assertThat(staged.getStatus()).isEqualTo(MediaAssetStatus.PROCESSING);
        assertThat(staged.getInstitution()).isEqualTo(institution);
        verify(mediaAssetRepository).saveAll(List.of(staged));
    }

    @Test
    void submit_filesNewMediaIntoAssignedAlbum_keepsLibraryAssetAlbum_andTagsBoth() {
        UUID submissionId = UUID.randomUUID();
        Instant scheduledAt = Instant.parse("2026-06-01T08:00:00Z");
        Submission submission = submission(submissionId, SubmissionStatus.draft, scheduledAt);
        submission.setMediaTags("expo, dost7");

        MediaAsset newUpload = new MediaAsset();
        newUpload.setId(UUID.randomUUID());
        newUpload.setStatus(MediaAssetStatus.STAGED);
        newUpload.setFileType(MediaFileType.jpeg);

        MediaAlbum originalAlbum = new MediaAlbum();
        originalAlbum.setId(UUID.randomUUID());
        MediaAsset libraryPick = mediaAsset(UUID.randomUUID(), institution);
        libraryPick.setStatus(MediaAssetStatus.READY);
        libraryPick.setMediaAlbum(originalAlbum);

        MediaAlbum assignedAlbum = new MediaAlbum();
        assignedAlbum.setId(UUID.randomUUID());
        assignedAlbum.setName("Research Expo Album");

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(guardRailService.validate(eq(institutionId), eq(scheduledAt), any())).thenReturn(new GuardRailResult());
        when(submissionRepository.save(submission)).thenReturn(submission);
        when(entityManager.getReference(User.class, contributorId)).thenReturn(contributor);
        when(submissionMediaAssetRepository.countBySubmissionId(submissionId)).thenReturn(2L);
        when(submissionMediaAssetRepository.findMediaAssetsBySubmissionId(submissionId))
                .thenReturn(List.of(newUpload, libraryPick));
        when(submissionMediaAssetRepository.findBySubmissionIdOrderByDisplayOrderAsc(submissionId)).thenReturn(List.of());
        when(userRepository.findByRole(UserRole.moderator)).thenReturn(List.of());
        when(mediaAlbumRepository.findByParentAndNameIgnoreCase(institutionId, null, "Research Expo Album"))
                .thenReturn(Optional.of(assignedAlbum));
        when(assetTagRepository.existsByMediaAssetIdAndLabel(any(), any())).thenReturn(false);

        submissionService.submit(submissionId, contributorPrincipal);

        // New upload: promoted and filed into the album the contributor assigned on the post.
        assertThat(newUpload.getStatus()).isEqualTo(MediaAssetStatus.PROCESSING);
        assertThat(newUpload.getMediaAlbum()).isEqualTo(assignedAlbum);
        // Existing library asset: album and status left untouched — only linked to the post.
        assertThat(libraryPick.getMediaAlbum()).isEqualTo(originalAlbum);
        assertThat(libraryPick.getStatus()).isEqualTo(MediaAssetStatus.READY);
        // Both assets receive the post's media tags (2 tags x 2 assets).
        verify(assetTagRepository, times(4)).save(any());
    }

    @Test
    void update_institutionChange_keepsSelectedMediaForNetworkRole() {
        UUID submissionId = UUID.randomUUID();
        UUID newInstitutionId = UUID.randomUUID();
        Institution newInstitution = institution(newInstitutionId);
        JwtUserDetails adminPrincipal = principal(contributorId, "moderator", institutionId);

        Submission submission = submission(submissionId, SubmissionStatus.draft, null);
        MediaAsset stagedAsset = new MediaAsset();
        stagedAsset.setId(UUID.randomUUID());
        stagedAsset.setStatus(MediaAssetStatus.STAGED);
        stagedAsset.setFileType(MediaFileType.jpeg);
        MediaAsset libraryPick = mediaAsset(UUID.randomUUID(), institution);
        libraryPick.setStatus(MediaAssetStatus.READY);

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(submissionRepository.save(submission)).thenReturn(submission);
        when(institutionRepository.findById(newInstitutionId)).thenReturn(Optional.of(newInstitution));
        when(submissionMediaAssetRepository.findBySubmissionIdOrderByDisplayOrderAsc(submissionId)).thenReturn(List.of());

        SubmissionUpdateDto dto = new SubmissionUpdateDto();
        dto.setInstitutionId(newInstitutionId);

        submissionService.update(submissionId, dto, adminPrincipal);

        verify(submissionMediaAssetRepository, never()).delete(any());
        verify(submissionMediaAssetRepository, never())
                .findBySubmissionIdAndMediaAssetId(submissionId, stagedAsset.getId());
        verify(submissionMediaAssetRepository, never())
                .findBySubmissionIdAndMediaAssetId(submissionId, libraryPick.getId());
        verify(slotReservationService).deleteAllForSubmission(submissionId);
        assertThat(submission.getInstitution()).isEqualTo(newInstitution);
        assertThat(submission.getScheduledAt()).isNull();
    }

    private SubmissionCreateDto createDto(Instant scheduledAt) {
        SubmissionCreateDto dto = new SubmissionCreateDto();
        dto.setEventTitle("Research Expo");
        dto.setEventDate(LocalDate.of(2026, 6, 1));
        dto.setCaption("Caption");
        dto.setDescription("Description");
        dto.setScheduledAt(scheduledAt);
        dto.setAlbumName("Research Expo Album");
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
        submission.setAlbumName("Research Expo Album");
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
