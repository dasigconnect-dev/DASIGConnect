package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetStatus;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionMediaContext;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionMediaContextRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmissionMediaContextService {

    public static final String MODEL_VERSION = "deterministic-context-v1";
    private static final int MAX_VALUES = 50;

    private final SubmissionRepository submissionRepository;
    private final SubmissionMediaAssetRepository mediaRepository;
    private final SubmissionMediaContextRepository contextRepository;
    private final ObjectMapper objectMapper;

    public SubmissionMediaContextService(
            SubmissionRepository submissionRepository,
            SubmissionMediaAssetRepository mediaRepository,
            SubmissionMediaContextRepository contextRepository,
            ObjectMapper objectMapper) {
        this.submissionRepository = submissionRepository;
        this.mediaRepository = mediaRepository;
        this.contextRepository = contextRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void rebuild(UUID submissionId) {
        Submission submission = submissionRepository.findById(submissionId).orElse(null);
        if (submission == null) return;

        List<MediaAsset> assets = mediaRepository.findMediaAssetsBySubmissionId(submissionId);
        String assetSetHash = hashAssetSet(assets);
        SubmissionMediaContext context = contextRepository.findById(submissionId)
                .orElseGet(SubmissionMediaContext::new);
        if (assetSetHash.equals(context.getAssetSetHash())
                && MODEL_VERSION.equals(context.getModelVersion())) return;

        List<MediaAsset> ready = assets.stream()
                .filter(asset -> asset.getStatus() == MediaAssetStatus.READY)
                .toList();
        int processing = (int) assets.stream().filter(asset ->
                asset.getStatus() == MediaAssetStatus.PROCESSING
                        || asset.getStatus() == MediaAssetStatus.STAGED).count();
        int failed = (int) assets.stream()
                .filter(asset -> asset.getStatus() == MediaAssetStatus.FAILED).count();

        Set<String> scenes = collect(ready, MediaAsset::getObservedScenes);
        Set<String> objects = collect(ready, MediaAsset::getVisibleObjects);
        Set<String> activities = collect(ready, MediaAsset::getObservedActivities);
        Set<String> peopleCounts = collectStrings(ready, MediaAsset::getPeopleCountRange);
        Set<String> equipment = collect(ready, MediaAsset::getEquipmentSignals);
        Set<String> recognition = collect(ready, MediaAsset::getRecognitionSignals);
        Set<String> ocrText = collect(ready, MediaAsset::getOcrText);
        Set<String> quality = collect(ready, MediaAsset::getVisualQualitySignals);
        Set<String> composition = collect(ready, MediaAsset::getCompositionSignals);
        ArrayNode eventHypotheses = mergeEventHypotheses(ready);
        Map<String, Object> temporal = new LinkedHashMap<>();
        temporal.put("classifications", collectStrings(ready, MediaAsset::getTemporalClassification));
        temporal.put("visibleDates", collect(ready, MediaAsset::getVisibleDates));
        temporal.put("possibleExpirations", collectStrings(ready, MediaAsset::getPossibleExpiration));

        Instant now = Instant.now();
        context.setSubmissionId(submissionId);
        context.setInstitutionId(submission.getInstitution().getId());
        context.setContextVersion(Math.max(0, context.getContextVersion()) + 1);
        context.setAssetSetHash(assetSetHash);
        context.setReadyAssetCount(ready.size());
        context.setProcessingAssetCount(processing);
        context.setFailedAssetCount(failed);
        context.setObservedScenes(json(scenes));
        context.setObservedObjects(json(objects));
        context.setObservedActivities(json(activities));
        context.setPeopleCountRanges(json(peopleCounts));
        context.setEquipmentSignals(json(equipment));
        context.setRecognitionSignals(json(recognition));
        context.setOcrText(json(ocrText));
        context.setEventHypotheses(eventHypotheses.toString());
        context.setTemporalSignals(json(temporal));
        context.setQualitySignals(json(quality));
        context.setCompositionSignals(json(composition));
        context.setContextText(buildContextText(
                scenes, objects, activities, peopleCounts, equipment, recognition, ocrText,
                eventHypotheses, temporal));
        context.setStatus(ready.isEmpty() ? "EMPTY" : processing > 0 || failed > 0 ? "PARTIAL" : "READY");
        context.setModelVersion(MODEL_VERSION);
        context.setGeneratedAt(now);
        context.setUpdatedAt(now);
        contextRepository.save(context);
    }

    private ArrayNode mergeEventHypotheses(List<MediaAsset> assets) {
        Map<String, JsonNode> bestByType = new LinkedHashMap<>();
        for (MediaAsset asset : assets) {
            try {
                JsonNode values = objectMapper.readTree(asset.getEventHypotheses());
                if (!values.isArray()) continue;
                for (JsonNode value : values) {
                    String eventType = value.path("eventType").asText(value.path("event_type").asText("")).trim();
                    if (eventType.isBlank()) continue;
                    String key = eventType.toLowerCase();
                    JsonNode current = bestByType.get(key);
                    if (current == null || value.path("confidence").asDouble() > current.path("confidence").asDouble()) {
                        bestByType.put(key, value);
                    }
                }
            } catch (Exception ignored) {
                // A malformed legacy field must not block context for other assets.
            }
        }
        ArrayNode result = objectMapper.createArrayNode();
        bestByType.values().stream()
                .sorted(Comparator.comparingDouble((JsonNode value) -> value.path("confidence").asDouble()).reversed())
                .limit(12)
                .forEach(result::add);
        return result;
    }

    private String buildContextText(
            Set<String> scenes, Set<String> objects, Set<String> activities,
            Set<String> peopleCounts, Set<String> equipment, Set<String> recognition,
            Set<String> ocrText, ArrayNode hypotheses, Map<String, Object> temporal) {
        List<String> parts = new ArrayList<>();
        append(parts, "scenes", scenes);
        append(parts, "objects", objects);
        append(parts, "activities", activities);
        append(parts, "people count ranges", peopleCounts);
        append(parts, "equipment", equipment);
        append(parts, "recognition signals", recognition);
        append(parts, "visible text", ocrText);
        List<String> events = new ArrayList<>();
        hypotheses.forEach(value -> events.add(value.path("eventType")
                .asText(value.path("event_type").asText(""))));
        append(parts, "possible events", events);
        append(parts, "visible dates", (Collection<?>) temporal.get("visibleDates"));
        return String.join(". ", parts);
    }

    private static void append(List<String> parts, String label, Collection<?> values) {
        List<String> strings = values.stream().map(String::valueOf).filter(value -> !value.isBlank()).toList();
        if (!strings.isEmpty()) parts.add(label + ": " + String.join(", ", strings));
    }

    private static Set<String> collect(List<MediaAsset> assets,
                                       java.util.function.Function<MediaAsset, String[]> getter) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (MediaAsset asset : assets) {
            String[] source = getter.apply(asset);
            if (source == null) continue;
            Arrays.stream(source).filter(value -> value != null && !value.isBlank())
                    .map(String::trim).forEach(values::add);
            if (values.size() >= MAX_VALUES) break;
        }
        return values;
    }

    private static Set<String> collectStrings(List<MediaAsset> assets,
                                               java.util.function.Function<MediaAsset, String> getter) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (MediaAsset asset : assets) {
            String value = getter.apply(asset);
            if (value != null && !value.isBlank()) values.add(value.trim());
        }
        return values;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("Could not serialize submission media context", error);
        }
    }

    private static String hashAssetSet(List<MediaAsset> assets) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            assets.forEach(asset -> {
                String value = asset.getId() + ":" + asset.getStatus() + ":"
                        + String.valueOf(asset.getAiProcessingVersion()) + ":"
                        + String.valueOf(asset.getAiClassifiedAt());
                digest.update(value.getBytes(StandardCharsets.UTF_8));
            });
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (Exception error) {
            throw new IllegalStateException("Could not hash submission media context", error);
        }
    }
}
