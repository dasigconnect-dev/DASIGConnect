package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.dasigconnect.backend.model.entity.AiInteractionLog;
import com.dasigconnect.backend.repository.AiInteractionLogRepository;

class AiAdoptionTrackingServiceTest {

    private final UUID submissionId = UUID.randomUUID();
    private final UUID institutionId = UUID.randomUUID();
    private AiInteractionLogRepository repository;
    private AiAdoptionTrackingService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(AiInteractionLogRepository.class);
        service = new AiAdoptionTrackingService(repository);
    }

    @Test
    void albumSuggestion_isStoredOnceWhileTheProposalIsUnchanged() {
        when(repository.findFirstBySubmissionIdAndInteractionTypeAndActionTakenOrderByCreatedAtDesc(
                submissionId, "album_match", "suggested"))
                .thenReturn(Optional.of(suggestion("ambiguous\nSeminars\nWorkshops")));

        service.recordAlbumSuggestion(submissionId, institutionId, "ambiguous", List.of("Seminars", "Workshops"));
        verify(repository, never()).save(any());

        service.recordAlbumSuggestion(submissionId, institutionId, "confident", List.of("Seminars"));
        AiInteractionLog saved = captureSaved();
        assertThat(saved.getActionTaken()).isEqualTo("suggested");
        assertThat(saved.getSuggestedValue()).isEqualTo("confident\nSeminars");
    }

    @Test
    void albumOutcome_isKeptWhenTheFinalAlbumIsOneTheAiProposed_ignoringCase() {
        stubLatestSuggestion("ambiguous\nSeminars\nWorkshops");

        service.recordAlbumOutcome(submissionId, institutionId, "  workshops ");

        assertThat(captureSaved().getActionTaken()).isEqualTo("kept");
    }

    @Test
    void albumOutcome_isChangedWhenTheFinalAlbumWasNotProposed() {
        stubLatestSuggestion("confident\nSeminars");

        service.recordAlbumOutcome(submissionId, institutionId, "Dasigtest");

        assertThat(captureSaved().getActionTaken()).isEqualTo("changed");
    }

    @Test
    void albumOutcome_isRecordedOncePerSubmission_andOnlyWhenSomethingWasProposed() {
        when(repository.existsBySubmissionIdAndInteractionTypeAndActionTakenIn(
                eq(submissionId), eq("album_match"), anyCollection())).thenReturn(true);
        service.recordAlbumOutcome(submissionId, institutionId, "Seminars");
        verify(repository, never()).save(any());

        UUID other = UUID.randomUUID();
        when(repository.findFirstBySubmissionIdAndInteractionTypeAndActionTakenOrderByCreatedAtDesc(
                other, "album_match", "suggested")).thenReturn(Optional.empty());
        service.recordAlbumOutcome(other, institutionId, "Seminars");
        verify(repository, never()).save(any());
    }

    @Test
    void templateDraft_recordsWithoutSubmission() {
        service.recordTemplateDraft(null, "generated");

        AiInteractionLog saved = captureSaved();
        assertThat(saved.getSubmissionId()).isNull();
        assertThat(saved.getInstitutionId()).isNull();
        assertThat(saved.getInteractionType()).isEqualTo("template_draft");
        assertThat(saved.getActionTaken()).isEqualTo("generated");
    }

    private void stubLatestSuggestion(String proposal) {
        when(repository.existsBySubmissionIdAndInteractionTypeAndActionTakenIn(
                eq(submissionId), eq("album_match"), anyCollection())).thenReturn(false);
        when(repository.findFirstBySubmissionIdAndInteractionTypeAndActionTakenOrderByCreatedAtDesc(
                submissionId, "album_match", "suggested")).thenReturn(Optional.of(suggestion(proposal)));
    }

    private static AiInteractionLog suggestion(String proposal) {
        AiInteractionLog log = new AiInteractionLog();
        log.setActionTaken("suggested");
        log.setSuggestedValue(proposal);
        return log;
    }

    private AiInteractionLog captureSaved() {
        ArgumentCaptor<AiInteractionLog> captor = ArgumentCaptor.forClass(AiInteractionLog.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void recordsAProofreadCheckWithItsFindingCount() {
        UUID submissionId = UUID.randomUUID();

        service.recordProofreadCheck(submissionId, null, 3);

        ArgumentCaptor<AiInteractionLog> saved = ArgumentCaptor.forClass(AiInteractionLog.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getInteractionType()).isEqualTo("proofread");
        assertThat(saved.getValue().getActionTaken()).isEqualTo("checked");
        assertThat(saved.getValue().getSuggestedValue()).isEqualTo("3");
        assertThat(saved.getValue().getSubmissionId()).isEqualTo(submissionId);
    }

    @Test
    void recordsAnAppliedProofreadFix() {
        service.recordProofreadFixApplied(null, null);

        ArgumentCaptor<AiInteractionLog> saved = ArgumentCaptor.forClass(AiInteractionLog.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getInteractionType()).isEqualTo("proofread");
        assertThat(saved.getValue().getActionTaken()).isEqualTo("applied");
    }
}
