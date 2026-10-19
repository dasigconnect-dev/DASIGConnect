package com.dasigconnect.backend.external;

import com.dasigconnect.backend.config.PaymongoProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PaymongoClient {
    private final PaymongoProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public PaymongoClient(PaymongoProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build());
    }

    PaymongoClient(PaymongoProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public PaymentLink createPaymentLink(long amountCentavos, String description, String remarks) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("amount", amountCentavos);
        payload.put("currency", "PHP");
        payload.put("description", description);
        payload.put("remarks", remarks);
        return parseLink(send("POST", "/payment_links", payload), List.of());
    }

    public PaymentLink retrievePaymentLink(String externalLinkId) {
        JsonNode link = send("GET", "/payment_links/" + externalLinkId, null);
        List<Payment> payments = parsePayments(
                send("GET", "/payment_links/" + externalLinkId + "/payments", null));
        return parseLink(link, payments);
    }

    public PaymentLink archivePaymentLink(String externalLinkId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("archive", true);
        return parseLink(send("PATCH", "/payment_links/" + externalLinkId, payload), List.of());
    }

    public Refund createRefund(String externalPaymentId, long amountCentavos, String reason, String notes) {
        ObjectNode attributes = objectMapper.createObjectNode();
        attributes.put("amount", amountCentavos);
        attributes.put("payment_id", externalPaymentId);
        attributes.put("reason", reason);
        if (notes != null && !notes.isBlank()) attributes.put("notes", notes);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.putObject("data").set("attributes", attributes);
        JsonNode data = send("POST", "/refunds", payload).path("data");
        JsonNode value = data.path("attributes");
        return new Refund(
                requiredText(data, "id"),
                value.path("amount").asLong(),
                value.path("status").asText("pending"),
                value.path("reason").asText(reason));
    }

    private JsonNode send(String method, String path, JsonNode payload) {
        if (!properties.enabled()) throw new PaymongoApiException("PayMongo integration is disabled.");
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(properties.apiBaseUrl() + path))
                    .timeout(Duration.ofSeconds(25))
                    .header("Accept", "application/json")
                    .header("Authorization", "Basic " + Base64.getEncoder()
                            .encodeToString((properties.secretKey() + ":").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            if (payload == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PaymongoApiException("PayMongo returned HTTP " + response.statusCode()
                        + ": " + providerMessage(response.body()));
            }
            return objectMapper.readTree(response.body());
        } catch (PaymongoApiException error) {
            throw error;
        } catch (java.net.http.HttpTimeoutException error) {
            throw new PaymongoApiException("PayMongo request timed out.", error);
        } catch (Exception error) {
            throw new PaymongoApiException("PayMongo request failed: " + error.getMessage(), error);
        }
    }

    private PaymentLink parseLink(JsonNode root, List<Payment> payments) {
        JsonNode data = root.path("data");
        JsonNode value = data.path("attributes").isObject() ? data.path("attributes") : data;
        String checkoutUrl = value.path("url").asText(value.path("checkout_url").asText(""));
        if (checkoutUrl.isBlank()) {
            throw new PaymongoApiException("PayMongo response is missing url.");
        }
        return new PaymentLink(
                requiredText(data, "id"),
                checkoutUrl,
                value.path("reference_number").asText(null),
                value.path("amount").asLong(),
                value.path("status").asText("active"),
                value.path("livemode").asBoolean(properties.liveMode()),
                payments);
    }

    private List<Payment> parsePayments(JsonNode root) {
        List<Payment> payments = new ArrayList<>();
        JsonNode data = root.path("data");
        if (!data.isArray()) return payments;
        for (JsonNode payment : data) {
            JsonNode value = payment.path("attributes").isObject() ? payment.path("attributes") : payment;
            String id = payment.path("id").asText(
                    value.path("id").asText(value.path("payment_id").asText("")));
            if (!id.isBlank()) {
                payments.add(new Payment(
                        id,
                        value.path("amount").asLong(),
                        value.path("currency").asText("PHP"),
                        value.path("status").asText("paid"),
                        providerInstant(value)));
            }
        }
        return payments;
    }

    private String providerMessage(String body) {
        try {
            JsonNode errors = objectMapper.readTree(body).path("errors");
            if (errors.isArray() && !errors.isEmpty()) {
                String detail = errors.get(0).path("detail").asText("Provider request failed");
                return detail.length() <= 300 ? detail : detail.substring(0, 300);
            }
        } catch (Exception ignored) {
            // Use a generic provider message when its error body is not JSON.
        }
        return "Provider request failed";
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) throw new PaymongoApiException("PayMongo response is missing " + field + ".");
        return value;
    }
    private static Instant providerInstant(JsonNode value) {
        JsonNode timestamp = value.hasNonNull("paid_at") ? value.path("paid_at") : value.path("created_at");
        if (timestamp.isNumber()) {
            long epoch = timestamp.asLong();
            if (epoch > 0) return Instant.ofEpochSecond(epoch);
        } else if (timestamp.isTextual() && !timestamp.asText().isBlank()) {
            try {
                return Instant.parse(timestamp.asText());
            } catch (java.time.format.DateTimeParseException ignored) {
                // Fall back to receipt time when the provider timestamp is not ISO-8601.
            }
        }
        return Instant.now();
    }

    public record PaymentLink(
            String id, String checkoutUrl, String referenceNumber, long amountCentavos,
            String status, boolean liveMode, List<Payment> payments) {}
    public record Payment(String id, long amountCentavos, String currency, String status, Instant paidAt) {}
    public record Refund(String id, long amountCentavos, String status, String reason) {}
}
