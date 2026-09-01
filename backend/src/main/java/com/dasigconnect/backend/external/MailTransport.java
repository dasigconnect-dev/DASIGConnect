package com.dasigconnect.backend.external;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Provider-neutral transport for outbound transactional email.
 *
 * <p>Talks to an HTTP mail API (Resend-compatible: {@code POST {base}/emails}
 * with a Bearer key and a JSON body). The provider is a configuration detail —
 * {@code app.mail.api-base-url} and {@code app.mail.api-key} — not part of this
 * class's contract, so swapping providers does not touch calling code.
 */
@Component
public class MailTransport {

    private static final Logger log = LoggerFactory.getLogger(MailTransport.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiKey;
    private final String sendUrl;
    private final String fromHeader;
    private final String replyTo;

    public MailTransport(
            @Value("${app.mail.api-key:}") String apiKey,
            @Value("${app.mail.api-base-url:https://api.resend.com}") String apiBaseUrl,
            @Value("${app.mail.from:no-reply@dasigconnect.local}") String fromAddress,
            @Value("${app.mail.from.name:DASIGConnect}") String fromName,
            @Value("${app.mail.reply-to:${app.mail.from:no-reply@dasigconnect.local}}") String replyTo) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.sendUrl = apiBaseUrl.replaceAll("/+$", "") + "/emails";
        this.fromHeader = (fromName == null || fromName.isBlank())
                ? fromAddress
                : fromName + " <" + fromAddress + ">";
        this.replyTo = replyTo;
        log.info("Mail transport configured: endpoint={}, from={}, key={}",
                sendUrl, fromHeader, this.apiKey.isBlank() ? "<missing>" : "<set>");
    }

    /**
     * Sends one email. At least one of {@code htmlBody} / {@code textBody} must be non-blank.
     *
     * @throws MailTransportException if the API key is missing, the request cannot be sent,
     *                                or the provider returns a non-2xx response
     */
    public void send(String to, String subject, String htmlBody, String textBody, Map<String, String> headers) {
        if (apiKey.isBlank()) {
            throw new MailTransportException("Mail API key is not configured (app.mail.api-key).");
        }
        if ((htmlBody == null || htmlBody.isBlank()) && (textBody == null || textBody.isBlank())) {
            throw new MailTransportException("Email has no html or text body.");
        }

        String payload = buildPayload(to, subject, htmlBody, textBody, headers);
        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sendUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ex) {
            throw new MailTransportException("Mail API request could not be completed: " + ex.getMessage(), ex);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new MailTransportException(
                    "Mail API returned HTTP " + response.statusCode() + ": " + summarize(response.body()));
        }
    }

    private String buildPayload(String to, String subject, String htmlBody, String textBody,
            Map<String, String> headers) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("from", fromHeader);
        ArrayNode recipients = root.putArray("to");
        recipients.add(to);
        root.put("subject", subject == null ? "" : subject);
        if (replyTo != null && !replyTo.isBlank()) {
            root.put("reply_to", replyTo);
        }
        if (htmlBody != null && !htmlBody.isBlank()) {
            root.put("html", htmlBody);
        }
        if (textBody != null && !textBody.isBlank()) {
            root.put("text", textBody);
        }
        if (headers != null && !headers.isEmpty()) {
            ObjectNode headerNode = root.putObject("headers");
            headers.forEach(headerNode::put);
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            throw new MailTransportException("Unable to serialize email payload", ex);
        }
    }

    private static String summarize(String body) {
        if (body == null || body.isBlank()) {
            return "<empty response>";
        }
        String trimmed = body.strip();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) + "…" : trimmed;
    }
}
