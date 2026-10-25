package com.dasigconnect.backend.service;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.dasigconnect.backend.service.AIRecommendationService.MediaEvaluationCandidate;
import com.dasigconnect.backend.service.AIRecommendationService.MediaEvaluationSnapshot;
import com.dasigconnect.backend.service.AIRecommendationService.MediaEvaluationStrategy;
import com.dasigconnect.backend.service.AIRecommendationService.MediaEvaluationTimings;
import com.dasigconnect.backend.service.MediaSuggestionEvaluationService.CandidatePoolDataset;
import com.dasigconnect.backend.service.MediaSuggestionEvaluationService.CandidatePoolScenario;
import com.dasigconnect.backend.service.MediaSuggestionEvaluationService.EvaluationDataset;
import com.dasigconnect.backend.service.MediaSuggestionEvaluationService.EvaluationRequest;
import com.dasigconnect.backend.service.MediaSuggestionEvaluationService.EvaluationScenario;
import java.util.ArrayList;
import java.util.EnumMap;
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

    @Test
    void validateCandidatePool_acceptsThirtyScenariosWithoutLabels() {
        List<CandidatePoolScenario> scenarios = new ArrayList<>();
        for (int index = 0; index < 30; index++) {
            scenarios.add(poolScenario("scenario-" + index));
        }

        MediaSuggestionEvaluationService.validateCandidatePool(
                new CandidatePoolDataset("DASIG candidate pool", scenarios));

        assertThat(scenarios).hasSize(30);
    }

    @Test
    void generateLabelingDataset_unionsCandidatesWithoutExposingStrategyOrder() {
        AIRecommendationService recommendationService = mock(AIRecommendationService.class);
        var mediaAssetRepository = mock(com.dasigconnect.backend.repository.MediaAssetRepository.class);
        MediaSuggestionEvaluationService service =
                new MediaSuggestionEvaluationService(recommendationService, mediaAssetRepository);
        UUID institutionId = UUID.randomUUID();
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Map<MediaEvaluationStrategy, List<MediaEvaluationCandidate>> rankings =
                new EnumMap<>(MediaEvaluationStrategy.class);
        rankings.put(MediaEvaluationStrategy.IMAGE_ONLY,
                List.of(new MediaEvaluationCandidate(second, institutionId, 0.95)));
        rankings.put(MediaEvaluationStrategy.SEMANTIC_ONLY,
                List.of(new MediaEvaluationCandidate(first, institutionId, 0.91)));
        rankings.put(MediaEvaluationStrategy.HYBRID_V2,
                List.of(new MediaEvaluationCandidate(second, institutionId, 0.99)));
        rankings.put(MediaEvaluationStrategy.RECIPROCAL_RANK_FUSION, List.of());
        when(recommendationService.evaluateMediaStrategies(any(), any(), any()))
                .thenAnswer(invocation -> new MediaEvaluationSnapshot(
                        invocation.getArgument(0), institutionId, 1, 2,
                        Map.copyOf(rankings), new MediaEvaluationTimings(2, 3, 5)));
        List<CandidatePoolScenario> scenarios = new ArrayList<>();
        for (int index = 0; index < 30; index++) {
            scenarios.add(poolScenario("scenario-" + index));
        }

        EvaluationDataset result = service.generateLabelingDataset(
                new CandidatePoolDataset("DASIG candidate pool", scenarios));

        assertThat(result.scenarios()).hasSize(30);
        assertThat(result.scenarios().getFirst().relevanceLabels())
                .containsOnlyKeys(first.toString(), second.toString());
        assertThat(result.scenarios().getFirst().relevanceLabels().keySet())
                .containsExactly(first.toString(), second.toString());
        assertThat(result.scenarios().getFirst().relevanceLabels().values())
                .containsOnlyNulls();
    }

    @Test
    void generateLabelingDataset_rejectsCrossInstitutionCandidates() {
        AIRecommendationService recommendationService = mock(AIRecommendationService.class);
        var mediaAssetRepository = mock(com.dasigconnect.backend.repository.MediaAssetRepository.class);
        MediaSuggestionEvaluationService service =
                new MediaSuggestionEvaluationService(recommendationService, mediaAssetRepository);
        UUID institutionId = UUID.randomUUID();
        Map<MediaEvaluationStrategy, List<MediaEvaluationCandidate>> rankings =
                new EnumMap<>(MediaEvaluationStrategy.class);
        rankings.put(MediaEvaluationStrategy.IMAGE_ONLY, List.of(new MediaEvaluationCandidate(
                UUID.randomUUID(), UUID.randomUUID(), 0.95)));
        when(recommendationService.evaluateMediaStrategies(any(), any(), any()))
                .thenAnswer(invocation -> new MediaEvaluationSnapshot(
                        invocation.getArgument(0), institutionId, 1, 1,
                        Map.copyOf(rankings), new MediaEvaluationTimings(2, 3, 5)));
        List<CandidatePoolScenario> scenarios = new ArrayList<>();
        for (int index = 0; index < 30; index++) {
            scenarios.add(poolScenario("scenario-" + index));
        }

        assertThatThrownBy(() -> service.generateLabelingDataset(
                new CandidatePoolDataset("DASIG candidate pool", scenarios)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cross-institution");
    }
    private static EvaluationScenario scenario(String id, int grade) {
        return new EvaluationScenario(
                id,
                "startup bootcamp",
                UUID.randomUUID(),
                new EvaluationRequest(
                        "Startup bootcamp",
                        "Teams presented their prototypes.",
                        
                        List.of("startup", "pitching"),
                        List.of(UUID.randomUUID())),
                Map.of(UUID.randomUUID().toString(), grade));
    }

    private static CandidatePoolScenario poolScenario(String id) {
        return new CandidatePoolScenario(
                id,
                "startup bootcamp",
                UUID.randomUUID(),
                new EvaluationRequest(
                        "Startup bootcamp",
                        "Teams presented their prototypes.",
                        List.of("startup", "pitching"),
                        List.of(UUID.randomUUID())),
                List.of());
    }
}
