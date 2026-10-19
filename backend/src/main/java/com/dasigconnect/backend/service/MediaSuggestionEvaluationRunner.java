package com.dasigconnect.backend.service;

import com.dasigconnect.backend.service.MediaSuggestionEvaluationService.CandidatePoolDataset;
import com.dasigconnect.backend.service.MediaSuggestionEvaluationService.EvaluationDataset;
import com.dasigconnect.backend.service.MediaSuggestionEvaluationService.EvaluationReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Executes the read-only Phase 6 benchmark only when explicitly profiled. */
@Component
@Profile("media-ai-evaluation")
public class MediaSuggestionEvaluationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MediaSuggestionEvaluationRunner.class);
    private final MediaSuggestionEvaluationService evaluationService;
    private final ObjectMapper objectMapper;
    private final String datasetPath;
    private final String outputPath;
    private final String mode;

    public MediaSuggestionEvaluationRunner(
            MediaSuggestionEvaluationService evaluationService,
            ObjectMapper objectMapper,
            @Value("${app.ai.media-evaluation.dataset:}") String datasetPath,
            @Value("${app.ai.media-evaluation.output:media-ai-evaluation-report.json}") String outputPath,
            @Value("${app.ai.media-evaluation.mode:evaluate}") String mode) {
        this.evaluationService = evaluationService;
        this.objectMapper = objectMapper;
        this.datasetPath = datasetPath;
        this.outputPath = outputPath;
        this.mode = mode;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (datasetPath == null || datasetPath.isBlank()) {
            throw new IllegalStateException("MEDIA_AI_EVALUATION_DATASET must point to a JSON dataset.");
        }
        Path source = Path.of(datasetPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) {
            throw new IllegalStateException("Evaluation dataset does not exist: " + source);
        }

        Path destination = Path.of(outputPath).toAbsolutePath().normalize();
        if (destination.getParent() != null) {
            Files.createDirectories(destination.getParent());
        }
        if ("pool".equalsIgnoreCase(mode)) {
            CandidatePoolDataset dataset = objectMapper.readValue(source.toFile(), CandidatePoolDataset.class);
            EvaluationDataset labelingDataset = evaluationService.generateLabelingDataset(dataset);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(destination.toFile(), labelingDataset);
            log.info("Media suggestion candidate pool completed: scenarios={}, labelingTemplate={}",
                    labelingDataset.scenarios().size(), destination);
            return;
        }
        if (!"evaluate".equalsIgnoreCase(mode)) {
            throw new IllegalStateException(
                    "MEDIA_AI_EVALUATION_MODE must be either 'pool' or 'evaluate'.");
        }
        EvaluationDataset dataset = objectMapper.readValue(source.toFile(), EvaluationDataset.class);
        EvaluationReport report = evaluationService.evaluate(dataset);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(destination.toFile(), report);
        log.info("Media suggestion evaluation completed: scenarios={}, report={}",
                report.scenarioCount(), destination);
    }
}
