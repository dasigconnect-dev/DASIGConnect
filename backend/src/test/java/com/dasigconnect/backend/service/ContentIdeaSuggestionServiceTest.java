package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.dasigconnect.backend.external.ClaudeVisionClient;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.repository.SubmissionRepository;

class ContentIdeaSuggestionServiceTest {

    private SubmissionRepository submissionRepository;
    private ClaudeVisionClient claudeClient;
    private ContentIdeaSuggestionService service;

    @BeforeEach
    void setUp() {
        submissionRepository = Mockito.mock(SubmissionRepository.class);
        claudeClient = Mockito.mock(ClaudeVisionClient.class);
        service = new ContentIdeaSuggestionService(submissionRepository, claudeClient);
    }

    @Test
    void generateSuggestions_whenNoSignals_returnsEmptyList_A6a() {
        Institution inst = new Institution();
        inst.setId(UUID.randomUUID());
        inst.setName("CIT University");

        when(submissionRepository.findRecentAndHistoricalPostTitles(eq(inst.getId()))).thenReturn(List.of());
        when(submissionRepository.findRecentCategoriesFromOtherInstitutions(eq(inst.getId()), any())).thenReturn(List.of());

        List<String> suggestions = service.generateSuggestions(inst);
        assertThat(suggestions).isEmpty();
    }

    @Test
    void generateSuggestions_whenSignalsPresent_callsClaudeAndParsesBullets() {
        Institution inst = new Institution();
        inst.setId(UUID.randomUUID());
        inst.setName("CIT University");

        when(submissionRepository.findRecentAndHistoricalPostTitles(eq(inst.getId())))
                .thenReturn(List.of("Annual Innovation Day 2025"));
        when(submissionRepository.findRecentCategoriesFromOtherInstitutions(eq(inst.getId()), any()))
                .thenReturn(List.of("Research & Development", "Student Achievements"));

        when(claudeClient.generateText(anyString(), anyString())).thenReturn("""
                • Share highlights from upcoming campus research exhibitions.
                • Feature student innovators preparing for regional competitions.
                """);

        List<String> suggestions = service.generateSuggestions(inst);
        assertThat(suggestions).hasSize(2);
        assertThat(suggestions.get(0)).contains("Share highlights");
        assertThat(suggestions.get(1)).contains("Feature student innovators");
    }
}
