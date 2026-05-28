package com.dasigconnect.backend.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.StringJoiner;

/**
 * Calls the Voyage AI Embeddings API to generate 1024-dimensional vectors
 * for media asset text representations (UC-3.3).
 *
 * The returned embedding string is formatted as a PostgreSQL-compatible JSON
 * array "[0.1, 0.2, ...]" ready for use with the native updateEmbedding query.
 */
@Service
public class VoyageAIClient {

    private static final Logger log = LoggerFactory.getLogger(VoyageAIClient.class);
    private static final String API_URL = "https://api.voyageai.com/v1/embeddings";
    private static final String MODEL = "voyage-4-lite";
    private static final int OUTPUT_DIMENSION = 1024;

    @Value("${voyage.api.key:}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Generates a 1024-dimensional embedding for the given text.
     *
     * @param text the text to embed (filename + category + description + tags)
     * @return JSON array string "[0.1, 0.2, ...]" compatible with pgvector ::vector cast
     * @throws VoyageApiException when the API key is absent, the request fails, or the response is malformed
     */
    public String embed(String text) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new VoyageApiException("Voyage AI API key is not configured.");
        }
        if (text == null || text.isBlank()) {
            throw new VoyageApiException("Embedding text must not be blank.");
        }

        String payload = buildPayload(text);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.http.HttpTimeoutException e) {
            throw new VoyageApiException("Voyage AI request timed out.");
        } catch (Exception e) {
            throw new VoyageApiException("Voyage AI request failed: " + e.getMessage());
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("Voyage AI returned status {}: {}", response.statusCode(), response.body());
            throw new VoyageApiException("Voyage AI error (HTTP " + response.statusCode() + ").");
        }

        return parseEmbedding(response.body());
    }

    public String modelName() {
        return MODEL;
    }

    private String buildPayload(String text) {
        try {
            var root = objectMapper.createObjectNode();
            root.put("model", MODEL);
            root.put("output_dimension", OUTPUT_DIMENSION);
            var inputArray = objectMapper.createArrayNode();
            inputArray.add(text);
            root.set("input", inputArray);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new VoyageApiException("Failed to build Voyage AI payload: " + e.getMessage());
        }
    }

    private String parseEmbedding(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode embeddingNode = root.path("data").get(0).path("embedding");
            if (!embeddingNode.isArray() || embeddingNode.isEmpty()) {
                throw new VoyageApiException("Voyage AI returned empty embedding.");
            }
            if (embeddingNode.size() != OUTPUT_DIMENSION) {
                throw new VoyageApiException("Voyage AI returned " + embeddingNode.size()
                        + " dimensions; expected " + OUTPUT_DIMENSION + ".");
            }

            StringJoiner joiner = new StringJoiner(",", "[", "]");
            for (JsonNode val : embeddingNode) {
                joiner.add(val.asText());
            }
            return joiner.toString();
        } catch (VoyageApiException e) {
            throw e;
        } catch (Exception e) {
            throw new VoyageApiException("Failed to parse Voyage AI embedding: " + e.getMessage());
        }
    }

    public static class VoyageApiException extends RuntimeException {
        public VoyageApiException(String message) { super(message); }
    }
}
