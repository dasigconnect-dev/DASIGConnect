package com.dasigconnect.backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PaymongoProperties {
    public enum Mode { TEST, LIVE }

    private final boolean enabled;
    private final Mode mode;
    private final String apiBaseUrl;
    private final String secretKey;
    private final String webhookSecret;
    private final long signatureToleranceSeconds;

    public PaymongoProperties(
            @Value("${app.paymongo.enabled:false}") boolean enabled,
            @Value("${app.paymongo.mode:TEST}") String mode,
            @Value("${app.paymongo.api-base-url:https://api.paymongo.com/v1}") String apiBaseUrl,
            @Value("${app.paymongo.secret-key-test:}") String testSecretKey,
            @Value("${app.paymongo.secret-key-live:}") String liveSecretKey,
            @Value("${app.paymongo.webhook-secret-test:}") String testWebhookSecret,
            @Value("${app.paymongo.webhook-secret-live:}") String liveWebhookSecret,
            @Value("${app.paymongo.signature-tolerance-seconds:300}") long signatureToleranceSeconds) {
        this.enabled = enabled;
        this.mode = Mode.valueOf(mode == null ? "TEST" : mode.trim().toUpperCase());
        this.apiBaseUrl = apiBaseUrl.replaceAll("/+$", "");
        this.secretKey = clean(this.mode == Mode.LIVE ? liveSecretKey : testSecretKey);
        this.webhookSecret = clean(this.mode == Mode.LIVE ? liveWebhookSecret : testWebhookSecret);
        this.signatureToleranceSeconds = Math.max(30, signatureToleranceSeconds);
    }

    @PostConstruct
    void validate() {
        if (!enabled) return;
        String expectedPrefix = mode == Mode.LIVE ? "sk_live_" : "sk_test_";
        if (!secretKey.startsWith(expectedPrefix)) {
            throw new IllegalStateException("PayMongo " + mode + " mode requires a matching secret API key.");
        }
        if (webhookSecret.isBlank()) {
            throw new IllegalStateException("PayMongo webhook verification secret is required when integration is enabled.");
        }
        if (mode == Mode.LIVE && !apiBaseUrl.startsWith("https://api.paymongo.com/")) {
            throw new IllegalStateException("Live PayMongo mode must use the official HTTPS API endpoint.");
        }
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    public boolean enabled() { return enabled; }
    public Mode mode() { return mode; }
    public boolean liveMode() { return mode == Mode.LIVE; }
    public String apiBaseUrl() { return apiBaseUrl; }
    public String secretKey() { return secretKey; }
    public String webhookSecret() { return webhookSecret; }
    public long signatureToleranceSeconds() { return signatureToleranceSeconds; }
}
