package com.dasigconnect.backend.service;

import com.dasigconnect.backend.config.PaymongoProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PaymongoWebhookSignatureVerifier {
    private final PaymongoProperties properties;
    private final Clock clock;

    @Autowired
    public PaymongoWebhookSignatureVerifier(PaymongoProperties properties) {
        this(properties, Clock.systemUTC());
    }
    PaymongoWebhookSignatureVerifier(PaymongoProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public boolean isValid(byte[] payload, String signatureHeader) {
        if (!properties.enabled() || properties.webhookSecret().isBlank()
                || payload == null || signatureHeader == null) return false;
        Map<String, String> parts = parse(signatureHeader);
        String timestamp = parts.get("t");
        String supplied = parts.get(properties.liveMode() ? "li" : "te");
        if (timestamp == null || supplied == null || supplied.isBlank()) return false;
        try {
            long epoch = Long.parseLong(timestamp);
            long age = Math.abs(Instant.now(clock).getEpochSecond() - epoch);
            if (age > properties.signatureToleranceSeconds()) return false;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal((timestamp + "." + new String(payload, StandardCharsets.UTF_8))
                    .getBytes(StandardCharsets.UTF_8));
            byte[] actual = HexFormat.of().parseHex(supplied);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception error) {
            return false;
        }
    }

    private static Map<String, String> parse(String header) {
        Map<String, String> values = new HashMap<>();
        for (String part : header.split(",")) {
            int separator = part.indexOf('=');
            if (separator > 0) values.put(part.substring(0, separator).trim(), part.substring(separator + 1).trim());
        }
        return values;
    }
}
