package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.service.MediaSuggestionEvaluationService.EvaluationDataset;
import com.dasigconnect.backend.service.MediaSuggestionEvaluationService.EvaluationRequest;
import com.dasigconnect.backend.service.MediaSuggestionEvaluationService.EvaluationScenario;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.ApplicationArguments;

class MediaSuggestionEvaluationRunnerTest {

    @TempDir
    Path tempDirectory;

    @Test
    void run_poolModeWritesBlankHumanLabelTemplate() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        MediaSuggestionEvaluationService evaluationService = mock(MediaSuggestionEvaluationService.class);
        Path source = tempDirectory.resolve("pool.json");
        Path output = tempDirectory.resolve("labels.json");
        Files.writeString(source, "{\"name\":\"pilot\",\"scenarios\":[]}");
        UUID candidateId = UUID.randomUUID();
        Map<String, Integer> labels = new LinkedHashMap<>();
        labels.put(candidateId.toString(), null);
        EvaluationDataset labelingDataset = new EvaluationDataset(
                "pilot",
                List.of(new EvaluationScenario(
                        "scenario-1",
                        "startup bootcamp",
                        UUID.randomUUID(),
                        new EvaluationRequest(null, null, List.of(), List.of()),
                        labels)));
        when(evaluationService.generateLabelingDataset(any())).thenReturn(labelingDataset);
        MediaSuggestionEvaluationRunner runner = new MediaSuggestionEvaluationRunner(
                evaluationService,
                objectMapper,
                source.toString(),
                output.toString(),
                "pool");

        runner.run(mock(ApplicationArguments.class));

        JsonNode written = objectMapper.readTree(output.toFile());
        assertThat(written.at("/scenarios/0/relevanceLabels/" + candidateId).isNull()).isTrue();
        verify(evaluationService).generateLabelingDataset(any());
        verifyNoMoreInteractions(evaluationService);
    }
}
