package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dasigconnect.backend.service.AIRecommendationService.MediaEvaluationCandidate;
import com.dasigconnect.backend.service.MediaSuggestionEvaluationService.EvaluationDataset;
import com.dasigconnect.backend.service.MediaSuggestionEvaluationService.EvaluationRequest;
import com.dasigconnect.backend.service.MediaSuggestionEvaluationService.EvaluationScenario;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MediaSuggestionEvaluationServiceTest {

    @Test
    void calculateScenarioMetrics_usesHumanGradesAndFlagsTenantLeakage() {
        UUID institutionId = UUID.randomUUID();
        UUID relevant = UUID.randomUUID();
        UUID irrelevant = UUID.randomUUID();
        UUID crossTenant = UUID.randomUUID();
        List<MediaEvaluationCandidate> candidates = List.of(
                new MediaEvaluationCandidate(relevant, institutionId, 0.91),
                new MediaEvaluationCandidate(irrelevant, institutionId, 0.82),
                new MediaEvaluationCandidate(crossTenant, UUID.randomUUID(), 0.75));

        var metrics = MediaSuggestionEvaluationService.calculateScenarioMetrics(
                candidates,
                Map.of(relevant, 3, irrelevant, 0, crossTenant, 2),
                institutionId);

        assertThat(metrics.topResultRelevant()).isTrue();
        assertThat(metrics.precisionAtFive()).isEqualTo(0.4);
        assertThat(metrics.ndcgAtTen()).isGreaterThan(0.8);
        assertThat(metrics.noResult()).isFalse();
        assertThat(metrics.crossInstitutionResults()).isEqualTo(1);
    }

    @Test
    void ndcgAtTen_isOneForIdealRanking() {
        UUID institutionId = UUID.randomUUID();
        UUID high = UUID.randomUUID();
        UUID relevant = UUID.randomUUID();
        UUID partial = UUID.randomUUID();
        List<MediaEvaluationCandidate> candidates = List.of(
                new MediaEvaluationCandidate(high, institutionId, 0.9),
                new MediaEvaluationCandidate(relevant, institutionId, 0.8),
                new MediaEvaluationCandidate(partial, institutionId, 0.7));

        double ndcg = MediaSuggestionEvaluationService.ndcgAtTen(
                candidates, Map.of(high, 3, relevant, 2, partial, 1));

        assertThat(ndcg).isEqualTo(1.0);
    }

    @Test
    void validate_rejectsSmallOrUnlabeledDatasets() {
        EvaluationDataset tooSmall = new EvaluationDataset(
                "DASIG pilot", List.of(scenario("scenario-1", 3)));
        assertThatThrownBy(() -> MediaSuggestionEvaluationService.validate(tooSmall))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least 30");

        List<EvaluationScenario> scenarios = new ArrayList<>();
        for (int index = 0; index < 30; index++) {
            scenarios.add(scenario("scenario-" + index, 0));
        }
        assertThatThrownBy(() -> MediaSuggestionEvaluationService.validate(
                new EvaluationDataset("DASIG pilot", scenarios)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one relevant asset");
    }

    @Test
    void validate_acceptsThirtyHumanLabeledScenarios() {
        List<EvaluationScenario> scenarios = new ArrayList<>();
        for (int index = 0; index < 30; index++) {
            scenarios.add(scenario("scenario-" + index, 2));
        }

        MediaSuggestionEvaluationService.validate(new EvaluationDataset("DASIG pilot", scenarios));

        assertThat(scenarios).hasSize(30);
    }

    private static EvaluationScenario scenario(String id, int grade) {
        return new EvaluationScenario(
                id,
                "startup bootcamp",
                UUID.randomUUID(),
                new EvaluationRequest(
                        "Startup bootcamp",
                        "Teams presented their prototypes.",
                        "Event",
                        List.of("startup", "pitching"),
                        List.of(UUID.randomUUID())),
                Map.of(UUID.randomUUID().toString(), grade));
    }
}
