package com.dasigconnect.backend.service;
import com.dasigconnect.backend.model.dto.ai.MediaSuggestRequestDto;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.AIRecommendationService.MediaEvaluationCandidate;
import com.dasigconnect.backend.service.AIRecommendationService.MediaEvaluationSnapshot;
import com.dasigconnect.backend.service.AIRecommendationService.MediaEvaluationStrategy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
/** Runs labeled, read-only relevance experiments for Phase 6. */
@Service
public class MediaSuggestionEvaluationService {
    static final int MINIMUM_SCENARIOS = 30;
    private static final int RELEVANT_GRADE = 2;
    private final AIRecommendationService recommendationService;
    private final MediaAssetRepository mediaAssetRepository;
    public MediaSuggestionEvaluationService(
            AIRecommendationService recommendationService,
            MediaAssetRepository mediaAssetRepository) {
        this.recommendationService = recommendationService;
        this.mediaAssetRepository = mediaAssetRepository;
    }
    public EvaluationReport evaluate(EvaluationDataset dataset) {
        validate(dataset);
        List<ScenarioResult> scenarios = new ArrayList<>();
        for (EvaluationScenario scenario : dataset.scenarios()) {
            MediaSuggestRequestDto request = toRequest(scenario.request());
            JwtUserDetails evaluationActor = new JwtUserDetails(
                    UUID.randomUUID(), "media-evaluation@local.invalid", "admin", null, true);
            MediaEvaluationSnapshot snapshot = recommendationService.evaluateMediaStrategies(
                    scenario.submissionId(), request, evaluationActor);
            Map<UUID, Integer> labels = parseLabels(scenario.relevanceLabels());
            validateLabeledAssets(scenario.id(), labels.keySet(), snapshot.institutionId());
            Map<MediaEvaluationStrategy, StrategyScenarioMetrics> strategyMetrics =
                    new EnumMap<>(MediaEvaluationStrategy.class);
            snapshot.rankings().forEach((strategy, candidates) -> strategyMetrics.put(
                    strategy,
                    calculateScenarioMetrics(candidates, labels, snapshot.institutionId())));
            scenarios.add(new ScenarioResult(
                    scenario.id(),
                    scenario.topic(),
                    snapshot.submissionId(),
                    snapshot.selectedImageCount(),
                    snapshot.candidateCount(),
                    Map.copyOf(strategyMetrics),
                    snapshot.timings()));
        }
        Map<MediaEvaluationStrategy, AggregateMetrics> aggregates =
                new EnumMap<>(MediaEvaluationStrategy.class);
        for (MediaEvaluationStrategy strategy : MediaEvaluationStrategy.values()) {
            aggregates.put(strategy, aggregate(strategy, scenarios));
        }
        return new EvaluationReport(
                dataset.name(),
                Instant.now(),
                scenarios.size(),
                Map.copyOf(aggregates),
                latencySummary(scenarios),
                List.copyOf(scenarios),
                "Human relevance grades: 0=not relevant, 1=somewhat relevant, "
                        + "2=relevant, 3=highly relevant. A relevant result has grade >= 2.");
    }
    static StrategyScenarioMetrics calculateScenarioMetrics(
            List<MediaEvaluationCandidate> candidates,
            Map<UUID, Integer> labels,
            UUID expectedInstitutionId) {
        boolean topRelevant = !candidates.isEmpty()
                && labels.getOrDefault(candidates.getFirst().assetId(), 0) >= RELEVANT_GRADE;
        long relevantTopFive = candidates.stream()
                .limit(5)
                .filter(candidate -> labels.getOrDefault(candidate.assetId(), 0) >= RELEVANT_GRADE)
                .count();
        double precisionAtFive = relevantTopFive / 5.0;
        double ndcgAtTen = ndcgAtTen(candidates, labels);
        long crossInstitutionResults = candidates.stream()
                .filter(candidate -> candidate.institutionId() == null
                        || !candidate.institutionId().equals(expectedInstitutionId))
                .count();
        List<EvaluatedCandidate> rankedCandidates = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            MediaEvaluationCandidate candidate = candidates.get(index);
            rankedCandidates.add(new EvaluatedCandidate(
                    index + 1,
                    candidate.assetId(),
                    candidate.score(),
                    labels.getOrDefault(candidate.assetId(), 0),
                    candidate.institutionId()));
        }
        return new StrategyScenarioMetrics(
                topRelevant,
                round(precisionAtFive),
                round(ndcgAtTen),
                candidates.isEmpty(),
                crossInstitutionResults,
                List.copyOf(rankedCandidates));
    }
    static double ndcgAtTen(
            List<MediaEvaluationCandidate> candidates, Map<UUID, Integer> labels) {
        double dcg = 0.0;
        for (int index = 0; index < Math.min(10, candidates.size()); index++) {
            int grade = labels.getOrDefault(candidates.get(index).assetId(), 0);
            dcg += gain(grade) / log2(index + 2.0);
        }
        List<Integer> idealGrades = labels.values().stream()
                .sorted(Comparator.reverseOrder())
                .limit(10)
                .toList();
        double idealDcg = 0.0;
        for (int index = 0; index < idealGrades.size(); index++) {
            idealDcg += gain(idealGrades.get(index)) / log2(index + 2.0);
        }
        return idealDcg == 0.0 ? 0.0 : dcg / idealDcg;
    }
    static void validate(EvaluationDataset dataset) {
        if (dataset == null || dataset.scenarios() == null) {
            throw new IllegalArgumentException("An evaluation dataset with scenarios is required.");
        }
        if (dataset.name() == null || dataset.name().isBlank()) {
            throw new IllegalArgumentException("The evaluation dataset needs a name.");
        }
        if (dataset.scenarios().size() < MINIMUM_SCENARIOS) {
            throw new IllegalArgumentException(
                    "At least " + MINIMUM_SCENARIOS + " human-labeled scenarios are required.");
        }
        Set<String> scenarioIds = new HashSet<>();
        for (EvaluationScenario scenario : dataset.scenarios()) {
            if (scenario.id() == null || scenario.id().isBlank() || !scenarioIds.add(scenario.id())) {
                throw new IllegalArgumentException("Scenario IDs must be present and unique.");
            }
            if (scenario.submissionId() == null) {
                throw new IllegalArgumentException("Scenario " + scenario.id() + " needs a submissionId.");
            }
            Map<UUID, Integer> labels = parseLabels(scenario.relevanceLabels());
            if (labels.values().stream().noneMatch(grade -> grade >= RELEVANT_GRADE)) {
                throw new IllegalArgumentException(
                        "Scenario " + scenario.id() + " needs at least one relevant asset label.");
            }
        }
    }
    private static AggregateMetrics aggregate(
            MediaEvaluationStrategy strategy, List<ScenarioResult> scenarios) {
        List<StrategyScenarioMetrics> values = scenarios.stream()
                .map(scenario -> scenario.strategies().get(strategy))
                .toList();
        double size = values.size();
        double topRate = 100.0 * values.stream().filter(StrategyScenarioMetrics::topResultRelevant).count() / size;
        double precision = values.stream().mapToDouble(StrategyScenarioMetrics::precisionAtFive).average().orElse(0.0);
        double ndcg = values.stream().mapToDouble(StrategyScenarioMetrics::ndcgAtTen).average().orElse(0.0);
        double noResultRate = 100.0 * values.stream().filter(StrategyScenarioMetrics::noResult).count() / size;
        long crossInstitution = values.stream().mapToLong(StrategyScenarioMetrics::crossInstitutionResults).sum();
        return new AggregateMetrics(
                round(topRate),
                round(precision),
                round(ndcg),
                round(noResultRate),
                crossInstitution,
                topRate >= 70.0,
                crossInstitution == 0);
    }
    private static LatencySummary latencySummary(List<ScenarioResult> scenarios) {
        List<Long> visual = scenarios.stream()
                .map(result -> result.timings().visualRetrievalMs()).sorted().toList();
        List<Long> semantic = scenarios.stream()
                .map(result -> result.timings().semanticRetrievalMs()).sorted().toList();
        List<Long> total = scenarios.stream()
                .map(result -> result.timings().totalSuggestionMs()).sorted().toList();
        return new LatencySummary(
                average(visual), percentile95(visual),
                average(semantic), percentile95(semantic),
                average(total), percentile95(total));
    }
    private static Map<UUID, Integer> parseLabels(Map<String, Integer> rawLabels) {
        if (rawLabels == null || rawLabels.isEmpty()) {
            throw new IllegalArgumentException("Every scenario needs human relevance labels.");
        }
        Map<UUID, Integer> labels = new LinkedHashMap<>();
        rawLabels.forEach((assetId, grade) -> {
            if (grade == null || grade < 0 || grade > 3) {
                throw new IllegalArgumentException("Relevance grades must be between 0 and 3.");
            }
            try {
                labels.put(UUID.fromString(assetId), grade);
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("Invalid labeled media asset UUID: " + assetId, error);
            }
        });
        return Map.copyOf(labels);
    }
    private void validateLabeledAssets(
            String scenarioId, Set<UUID> labeledIds, UUID expectedInstitutionId) {
        List<MediaAsset> assets = mediaAssetRepository.findActiveByIds(List.copyOf(labeledIds));
        Set<UUID> foundIds = assets.stream().map(MediaAsset::getId).collect(java.util.stream.Collectors.toSet());
        if (!foundIds.containsAll(labeledIds)) {
            Set<UUID> missing = new HashSet<>(labeledIds);
            missing.removeAll(foundIds);
            throw new IllegalArgumentException(
                    "Scenario " + scenarioId + " labels missing or deleted assets: " + missing);
        }
        boolean crossInstitutionLabel = assets.stream().anyMatch(asset -> asset.getInstitution() == null
                || !expectedInstitutionId.equals(asset.getInstitution().getId()));
        if (crossInstitutionLabel) {
            throw new IllegalArgumentException(
                    "Scenario " + scenarioId + " contains a label from another institution.");
        }
    }
    private static MediaSuggestRequestDto toRequest(EvaluationRequest request) {
        EvaluationRequest safeRequest = request == null
                ? new EvaluationRequest(null, null, List.of(), List.of()) : request;
        MediaSuggestRequestDto dto = new MediaSuggestRequestDto();
        dto.setEventTitle(safeRequest.eventTitle());
        dto.setCaption(safeRequest.caption());
        dto.setTags(safeRequest.tags() == null ? List.of() : safeRequest.tags());
        dto.setSelectedAssetIds(safeRequest.selectedAssetIds() == null
                ? List.of() : safeRequest.selectedAssetIds());
        return dto;
    }
    private static double gain(int relevance) {
        return Math.pow(2.0, relevance) - 1.0;
    }
    private static double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }
    private static double average(List<Long> sortedValues) {
        return round(sortedValues.stream().mapToLong(Long::longValue).average().orElse(0.0));
    }
    private static long percentile95(List<Long> sortedValues) {
        if (sortedValues.isEmpty()) return 0;
        int index = (int) Math.ceil(sortedValues.size() * 0.95) - 1;
        return sortedValues.get(Math.max(0, index));
    }
    private static double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
    public record EvaluationDataset(String name, List<EvaluationScenario> scenarios) {
    }
    public record EvaluationScenario(
            String id,
            String topic,
            UUID submissionId,
            EvaluationRequest request,
            Map<String, Integer> relevanceLabels) {
    }
    public record EvaluationRequest(
            String eventTitle,
            String caption,
            List<String> tags,
            List<UUID> selectedAssetIds) {
    }
    public record StrategyScenarioMetrics(
            boolean topResultRelevant,
            double precisionAtFive,
            double ndcgAtTen,
            boolean noResult,
            long crossInstitutionResults,
            List<EvaluatedCandidate> rankedCandidates) {
    }
    public record EvaluatedCandidate(
            int rank,
            UUID assetId,
            double score,
            int relevanceGrade,
            UUID institutionId) {
    }
    public record AggregateMetrics(
            double topResultRelevantRatePercent,
            double meanPrecisionAtFive,
            double meanNdcgAtTen,
            double noResultRatePercent,
            long crossInstitutionResults,
            boolean topResultTargetMet,
            boolean tenantIsolationTargetMet) {
    }
    public record LatencySummary(
            double averageVisualRetrievalMs,
            long p95VisualRetrievalMs,
            double averageSemanticRetrievalMs,
            long p95SemanticRetrievalMs,
            double averageTotalSuggestionMs,
            long p95TotalSuggestionMs) {
    }
    public record ScenarioResult(
            String id,
            String topic,
            UUID submissionId,
            int selectedImageCount,
            int candidateCount,
            Map<MediaEvaluationStrategy, StrategyScenarioMetrics> strategies,
            AIRecommendationService.MediaEvaluationTimings timings) {
    }
    public record EvaluationReport(
            String datasetName,
            Instant generatedAt,
            int scenarioCount,
            Map<MediaEvaluationStrategy, AggregateMetrics> strategies,
            LatencySummary latency,
            List<ScenarioResult> scenarios,
            String relevanceScale) {
    }
}
