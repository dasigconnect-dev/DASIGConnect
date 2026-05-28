package com.dasigconnect.backend.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.external.ClaudeVisionClient;
import com.dasigconnect.backend.model.dto.ai.CaptionResponseDto;
import com.dasigconnect.backend.model.dto.ai.CaptionVariantDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionMediaAsset;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.repository.AiInteractionLogRepository;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaptionGenerationServiceTest {

    @Mock private SubmissionRepository submissionRepository;
    @Mock private SubmissionMediaAssetRepository submissionMediaAssetRepository;
    @Mock private ClaudeVisionClient claudeVisionClient;
    @Mock private AiInteractionLogRepository aiInteractionLogRepository;

    @InjectMocks
    private CaptionGenerationService service;

    // ── generateCaptions() ───────────────────────────────────────────────────

    @Test
    void generateCaptions_withImages_returnsVariants() {
        UUID submissionId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Submission s = submission(submissionId, institutionId);
        SubmissionMediaAsset sma = imageJunction(s, imageAsset(UUID.randomUUID()));

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));
        when(submissionMediaAssetRepository.findBySubmissionIdWithMediaAsset(submissionId))
                .thenReturn(List.of(sma));

        List<CaptionVariantDto> variants = List.of(
                new CaptionVariantDto("professional", "Professional caption #DASIG"),
                new CaptionVariantDto("community", "Community caption #DASIG"),
                new CaptionVariantDto("energetic", "Energetic caption! #DASIG")
        );
        when(claudeVisionClient.generateCaptions(any(), any(), any())).thenReturn(variants);
        when(aiInteractionLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CaptionResponseDto result = service.generateCaptions(submissionId, userId, institutionId, null);

        assertThat(result.getSubmissionId()).isEqualTo(submissionId);
        assertThat(result.getVariants()).hasSize(3);
        assertThat(result.getVariants().get(0).getTone()).isEqualTo("professional");
    }

    @Test
    void generateCaptions_noImages_throwsUnprocessableEntity() {
        UUID submissionId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Submission s = submission(submissionId, institutionId);
        SubmissionMediaAsset videoSma = videoJunction(s, videoAsset(UUID.randomUUID()));

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));
        when(submissionMediaAssetRepository.findBySubmissionIdWithMediaAsset(submissionId))
                .thenReturn(List.of(videoSma));

        assertThatThrownBy(() -> service.generateCaptions(submissionId, userId, institutionId, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(422));

        verify(claudeVisionClient, never()).generateCaptions(any(), any(), any());
    }

    @Test
    void generateCaptions_noMedia_throwsUnprocessableEntity() {
        UUID submissionId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();

        Submission s = submission(submissionId, institutionId);

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));
        when(submissionMediaAssetRepository.findBySubmissionIdWithMediaAsset(submissionId))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.generateCaptions(submissionId, UUID.randomUUID(), institutionId, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(422));
    }

    @Test
    void generateCaptions_wrongInstitution_throwsForbidden() {
        UUID submissionId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        UUID differentInstitutionId = UUID.randomUUID();

        Submission s = submission(submissionId, institutionId);

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.generateCaptions(submissionId, UUID.randomUUID(), differentInstitutionId, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void generateCaptions_adminWithNullInstitution_canAccessAnyInstitution() {
        UUID submissionId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();

        Submission s = submission(submissionId, institutionId);
        SubmissionMediaAsset sma = imageJunction(s, imageAsset(UUID.randomUUID()));

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));
        when(submissionMediaAssetRepository.findBySubmissionIdWithMediaAsset(submissionId))
                .thenReturn(List.of(sma));
        when(claudeVisionClient.generateCaptions(any(), any(), any())).thenReturn(List.of(
                new CaptionVariantDto("professional", "Caption #DASIG")
        ));
        when(aiInteractionLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // null institutionId = administrator bypasses institution check
        CaptionResponseDto result = service.generateCaptions(submissionId, UUID.randomUUID(), null, null);

        assertThat(result.getSubmissionId()).isEqualTo(submissionId);
    }

    @Test
    void generateCaptions_submissionNotFound_throwsNotFound() {
        UUID submissionId = UUID.randomUUID();
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateCaptions(submissionId, UUID.randomUUID(), UUID.randomUUID(), null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void generateCaptions_limitsImageUrlsToFour() {
        UUID submissionId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();

        Submission s = submission(submissionId, institutionId);
        List<SubmissionMediaAsset> fiveImages = List.of(
                imageJunction(s, imageAsset(UUID.randomUUID())),
                imageJunction(s, imageAsset(UUID.randomUUID())),
                imageJunction(s, imageAsset(UUID.randomUUID())),
                imageJunction(s, imageAsset(UUID.randomUUID())),
                imageJunction(s, imageAsset(UUID.randomUUID()))
        );

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(s));
        when(submissionMediaAssetRepository.findBySubmissionIdWithMediaAsset(submissionId))
                .thenReturn(fiveImages);
        when(claudeVisionClient.generateCaptions(any(), any(), any())).thenReturn(List.of(
                new CaptionVariantDto("professional", "Caption #DASIG")
        ));
        when(aiInteractionLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.generateCaptions(submissionId, UUID.randomUUID(), institutionId, null);

        // Claude client must receive at most 4 URLs
        verify(claudeVisionClient).generateCaptions(
                eq(fiveImages.stream()
                        .map(sma -> sma.getMediaAsset().getStorageUrl())
                        .limit(4)
                        .toList()),
                any(),
                any());
    }

    // ── logInteraction() ─────────────────────────────────────────────────────

    @Test
    void logInteraction_savesEntry() {
        UUID submissionId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        when(aiInteractionLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.logInteraction(submissionId, institutionId, "use", "professional");

        verify(aiInteractionLogRepository).save(any());
    }

    @Test
    void logInteraction_swallowsException() {
        UUID submissionId = UUID.randomUUID();
        when(aiInteractionLogRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        // Must not propagate
        service.logInteraction(submissionId, UUID.randomUUID(), "dismiss", null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Submission submission(UUID id, UUID institutionId) {
        Institution institution = new Institution();
        institution.setId(institutionId);
        institution.setName("CIT-U");
        institution.setCode("CIT-U");
        institution.setEmailDomain("cit.edu.ph");

        User contributor = new User();
        contributor.setId(UUID.randomUUID());
        contributor.setEmail("contributor@cit.edu.ph");
        contributor.setRole(UserRole.contributor);

        Submission s = new Submission();
        s.setId(id);
        s.setEventTitle("Science Fair 2026");
        s.setEventDate(LocalDate.of(2026, 7, 1));
        s.setStatus(SubmissionStatus.draft);
        s.setContributor(contributor);
        s.setInstitution(institution);
        return s;
    }

    private static MediaAsset imageAsset(UUID id) {
        MediaAsset a = new MediaAsset();
        a.setId(id);
        a.setStorageUrl("https://storage.example.com/" + id + ".jpg");
        a.setFileName(id + ".jpg");
        a.setFileType(MediaFileType.jpeg);
        a.setFileSizeBytes(204800L);
        return a;
    }

    private static MediaAsset videoAsset(UUID id) {
        MediaAsset a = new MediaAsset();
        a.setId(id);
        a.setStorageUrl("https://storage.example.com/" + id + ".mp4");
        a.setFileName(id + ".mp4");
        a.setFileType(MediaFileType.mp4);
        a.setFileSizeBytes(10485760L);
        return a;
    }

    private static SubmissionMediaAsset imageJunction(Submission s, MediaAsset a) {
        SubmissionMediaAsset sma = new SubmissionMediaAsset();
        sma.setId(UUID.randomUUID());
        sma.setSubmission(s);
        sma.setMediaAsset(a);
        sma.setDisplayOrder(0);
        return sma;
    }

    private static SubmissionMediaAsset videoJunction(Submission s, MediaAsset a) {
        SubmissionMediaAsset sma = new SubmissionMediaAsset();
        sma.setId(UUID.randomUUID());
        sma.setSubmission(s);
        sma.setMediaAsset(a);
        sma.setDisplayOrder(0);
        return sma;
    }
}
