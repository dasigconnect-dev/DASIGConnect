package com.dasigconnect.backend.external;

import com.dasigconnect.backend.model.dto.ai.EventHypothesisDto;
import com.dasigconnect.backend.model.dto.ai.MediaClassificationDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Uses Gemini multimodal understanding to produce bounded media metadata. */
@Service
public class GeminiVisionClient implements MediaClassificationClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiVisionClient.class);
    private static final String API_ROOT =
            "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final List<String> ALLOWED_CATEGORIES = List.of(
            "Food", "People", "Event", "Technology", "Research", "Education",
            "Sports", "Culture", "Nature", "Document", "Product", "Architecture",
            "Artwork", "Other");
    private static final List<String> ALLOWED_ASSET_TYPES = List.of(
            "Product Photo", "Food Photo", "Event Photo", "Lab Photo",
            "Project Presentation", "Poster", "Document", "Screenshot",
            "Portrait", "Group Photo", "Landscape", "Building Photo",
            "Artwork Photo", "Infographic", "Other");

    private final String apiKey;
    private final String model;
    private final ClaudeVisionClient imagePreparation;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GeminiVisionClient(
            @Value("${gemini.api.key:}") String apiKey,
            @Value("${gemini.api.model:gemini-3.8-flash}") String model,
            ClaudeVisionClient imagePreparation) {
        this(apiKey, model, imagePreparation,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                new ObjectMapper());
    }

    GeminiVisionClient(String apiKey, String model, ClaudeVisionClient imagePreparation,
            HttpClient httpClient, ObjectMapper objectMapper) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
        this.imagePreparation = imagePreparation;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public MediaClassificationDto classifyMedia(List<String> imageUrls) {
        if (apiKey.isBlank()) {
            throw new GeminiApiException("Gemini API key is not configured.");
        }
        if (model.isBlank() || !model.matches("[A-Za-z0-9._-]+")) {
            throw new GeminiApiException("Gemini model is not configured correctly.");
        }
        if (imageUrls == null || imageUrls.isEmpty()) {
            throw new GeminiApiException("At least one image URL is required for classification.");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_ROOT + model + ":generateContent"))
                .header("x-goog-api-key", apiKey)
                .header("content-type", "application/json")
                .timeout(Duration.ofSeconds(45))
                .POST(HttpRequest.BodyPublishers.ofString(buildPayload(imageUrls)))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Gemini classification returned status {}: {}", response.statusCode(),
                        abbreviate(response.body(), 1000));
                throw new GeminiApiException(
                        "Gemini API error (HTTP " + response.statusCode() + ").");
            }
            return parseResponse(response.body());
        } catch (java.net.http.HttpTimeoutException error) {
            throw new GeminiApiException("Gemini classification timed out.");
        } catch (GeminiApiException error) {
            throw error;
        } catch (Exception error) {
            throw new GeminiApiException(
                    "Gemini classification request failed: " + error.getMessage());
        }
    }

    @Override
    public String modelName() {
        return model;
    }

    private String buildPayload(List<String> imageUrls) {
        try {
            ArrayNode parts = objectMapper.createArrayNode();
            parts.addObject().put("text", classificationPrompt());
            for (String imageUrl : imageUrls.stream().limit(4).toList()) {
                ClaudeVisionClient.PreparedImage image =
                        imagePreparation.prepareImageForEmbedding(imageUrl);
                ObjectNode inlineData = parts.addObject().putObject("inlineData");
                inlineData.put("mimeType", image.mediaType());
                inlineData.put("data", Base64.getEncoder().encodeToString(image.bytes()));
            }

            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode content = root.putArray("contents").addObject();
            content.put("role", "user");
            content.set("parts", parts);
            ObjectNode generation = root.putObject("generationConfig");
            generation.put("responseMimeType", "application/json");
            generation.put("temperature", 0.1);
            generation.put("maxOutputTokens", 4096);
            generation.putObject("thinkingConfig").put("thinkingLevel", "LOW");
            generation.set("responseSchema", responseSchema());
            return objectMapper.writeValueAsString(root);
        } catch (GeminiApiException error) {
            throw error;
        } catch (Exception error) {
            throw new GeminiApiException(
                    "Failed to build Gemini classification request: " + error.getMessage());
        }
    }

    private ObjectNode responseSchema() {
        ObjectNode schema = objectMapper.createObjectNode().put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        stringProperty(properties, "primary_category");
        stringProperty(properties, "asset_type");
        stringProperty(properties, "ai_caption");
        for (String field : List.of(
                "visible_objects", "specific_subjects", "visual_style", "dominant_colors",
                "possible_use_cases", "ai_tags", "excluded_categories", "observed_scenes",
                "observed_activities", "equipment_signals", "recognition_signals", "ocr_text",
                "visible_dates", "visual_quality_signals", "composition_signals")) {
            stringArrayProperty(properties, field);
        }
        stringProperty(properties, "people_count_range");
        stringProperty(properties, "temporal_classification");
        stringProperty(properties, "possible_expiration");
        properties.putObject("confidence").put("type", "number");

        ObjectNode hypotheses = properties.putObject("event_hypotheses");
        hypotheses.put("type", "array");
        ObjectNode hypothesis = hypotheses.putObject("items").put("type", "object");
        ObjectNode hypothesisProperties = hypothesis.putObject("properties");
        stringProperty(hypothesisProperties, "event_type");
        hypothesisProperties.putObject("confidence").put("type", "number");
        stringArrayProperty(hypothesisProperties, "evidence");
        hypothesis.putArray("required").add("event_type").add("confidence").add("evidence");

        ArrayNode required = schema.putArray("required");
        properties.fieldNames().forEachRemaining(required::add);
        return schema;
    }

    private static void stringProperty(ObjectNode properties, String field) {
        properties.putObject(field).put("type", "string");
    }

    private static void stringArrayProperty(ObjectNode properties, String field) {
        ObjectNode property = properties.putObject(field);
        property.put("type", "array");
        property.putObject("items").put("type", "string");
    }

    private MediaClassificationDto parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
            String generatedJson = "";
            if (parts.isArray()) {
                for (JsonNode part : parts) {
                    if (part.hasNonNull("text")) generatedJson += part.path("text").asText();
                }
            }
            if (generatedJson.isBlank()) {
                throw new GeminiApiException("Gemini returned no classification content.");
            }
            return parseClassificationJson(generatedJson);
        } catch (GeminiApiException error) {
            throw error;
        } catch (Exception error) {
            log.warn("Failed to parse Gemini classification response: {}", error.getMessage());
            throw new GeminiApiException("Could not parse classification from Gemini response.");
        }
    }

    MediaClassificationDto parseClassificationJson(String generatedJson) {
        try {
            String json = generatedJson.strip();
            if (json.startsWith("```")) json = json.replaceAll("```[a-zA-Z]*\\s*", "").strip();
            JsonNode node = objectMapper.readTree(json);
            String category = firstText(node, "primary_category", "category");
            if (!ALLOWED_CATEGORIES.contains(category)) category = "Other";
            String assetType = node.path("asset_type").asText("").strip();
            if (!ALLOWED_ASSET_TYPES.contains(assetType)) assetType = "Other";
            double confidence = Math.min(1.0,
                    Math.max(0.0, node.path("confidence").asDouble(0.5)));
            List<String> tags = readStringArray(node, "ai_tags", 30, 60);
            if (tags.isEmpty()) tags = readStringArray(node, "suggestedTags", 30, 60);
            return new MediaClassificationDto(
                    category, assetType, confidence, firstText(node, "ai_caption", "description"),
                    readStringArray(node, "visible_objects", 20, 60),
                    readStringArray(node, "specific_subjects", 20, 80),
                    readStringArray(node, "visual_style", 15, 60),
                    readStringArray(node, "dominant_colors", 10, 40),
                    readStringArray(node, "possible_use_cases", 15, 80),
                    tags, readStringArray(node, "excluded_categories", 15, 80),
                    readStringArray(node, "observed_scenes", 15, 80),
                    readStringArray(node, "observed_activities", 20, 80),
                    node.path("people_count_range").asText("").strip(),
                    readStringArray(node, "equipment_signals", 15, 80),
                    readStringArray(node, "recognition_signals", 15, 80),
                    readStringArray(node, "ocr_text", 20, 160),
                    readStringArray(node, "visible_dates", 10, 60),
                    readEventHypotheses(node.path("event_hypotheses")),
                    node.path("temporal_classification").asText("").strip(),
                    node.path("possible_expiration").asText("").strip(),
                    readStringArray(node, "visual_quality_signals", 15, 80),
                    readStringArray(node, "composition_signals", 15, 80));
        } catch (Exception error) {
            throw new GeminiApiException("Could not parse classification from Gemini response.");
        }
    }

    private static String firstText(JsonNode node, String primary, String fallback) {
        String value = node.path(primary).asText("").strip();
        return value.isBlank() ? node.path(fallback).asText("").strip() : value;
    }

    private static List<String> readStringArray(
            JsonNode node, String fieldName, int maxItems, int maxLength) {
        JsonNode array = node.path(fieldName);
        if (!array.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonNode item : array) {
            String value = item.asText("").strip().replaceAll("\\s+", " ");
            if (value.isBlank()) continue;
            if (value.length() > maxLength) value = value.substring(0, maxLength).strip();
            if (!values.contains(value)) values.add(value);
            if (values.size() >= maxItems) break;
        }
        return List.copyOf(values);
    }

    private static List<EventHypothesisDto> readEventHypotheses(JsonNode array) {
        if (!array.isArray()) return List.of();
        List<EventHypothesisDto> hypotheses = new ArrayList<>();
        for (JsonNode item : array) {
            String eventType = item.path("event_type").asText("").strip();
            if (eventType.isBlank()) continue;
            double confidence = Math.min(1.0,
                    Math.max(0.0, item.path("confidence").asDouble(0.0)));
            hypotheses.add(new EventHypothesisDto(eventType, confidence,
                    readStringArray(item, "evidence", 8, 100)));
            if (hypotheses.size() >= 8) break;
        }
        return List.copyOf(hypotheses);
    }

    private static String classificationPrompt() {
        return """
                Analyze the supplied image for a university media library. Identify what is
                actually visible; do not force academic, technology, or event labels without
                visual evidence. The caption must be factual and neutral. Tags must describe
                visible subjects, objects, setting, style, readable text, or likely use cases.
                Event hypotheses are probabilities, not facts. OCR and dates must include only
                legible text. Do not identify private people by name.

                Allowed primary_category: Food, People, Event, Technology, Research, Education,
                Sports, Culture, Nature, Document, Product, Architecture, Artwork, Other.
                Allowed asset_type: Product Photo, Food Photo, Event Photo, Lab Photo,
                Project Presentation, Poster, Document, Screenshot, Portrait, Group Photo,
                Landscape, Building Photo, Artwork Photo, Infographic, Other.
                Distinguish graphic formats carefully:
                - Infographic explains information through sections, diagrams, charts, icons,
                  statistics, or a structured educational layout.
                - Poster primarily promotes or announces one event, person, product, or call
                  to action, even when it contains dates, logos, and several text blocks.
                - Screenshot visibly captures a website, social post, application, video call,
                  or other software interface.
                - Project Presentation is an individual presentation slide or a captured slide
                  deck layout, usually with a slide title and presentation-oriented composition.
                Do not classify every text-heavy graphic as an Infographic. Choose the format
                from visible layout and communication purpose, not from filename alone.
                Use 0, 1, 2-5, 6-20, or 20+ for people_count_range. Use evergreen, time_bound,
                expired, or unknown for temporal_classification. Return 8-30 useful ai_tags.
                """;
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null) return "";
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").strip();
        return normalized.length() <= maxLength
                ? normalized : normalized.substring(0, maxLength);
    }

    public static class GeminiApiException extends RuntimeException {
        public GeminiApiException(String message) {
            super(message);
        }
    }
}
