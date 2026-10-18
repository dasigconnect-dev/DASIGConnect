package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dasigconnect.backend.config.PaymongoProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class PaymongoWebhookSignatureVerifierTest {
    private static final Instant NOW = Instant.parse("2026-10-18T04:00:00Z");
    private static final String SECRET = "whsk_test_example";

    @Test
    void isValid_acceptsCurrentTestSignatureForRawPayload() throws Exception {
        byte[] payload = "{\"data\":{\"id\":\"evt_1\"}}".getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(NOW.getEpochSecond());
        String header = "t=" + timestamp + ",te=" + sign(timestamp, payload) + ",li=";

        assertThat(verifier().isValid(payload, header)).isTrue();
    }

    @Test
    void isValid_rejectsTamperedAndExpiredRequests() throws Exception {
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);
        String oldTimestamp = Long.toString(NOW.minusSeconds(301).getEpochSecond());

        assertThat(verifier().isValid(payload,
                "t=" + oldTimestamp + ",te=" + sign(oldTimestamp, payload) + ",li=")).isFalse();
        assertThat(verifier().isValid("tampered".getBytes(StandardCharsets.UTF_8),
                "t=" + NOW.getEpochSecond() + ",te=" + sign(Long.toString(NOW.getEpochSecond()), payload) + ",li="))
                .isFalse();
    }

    private PaymongoWebhookSignatureVerifier verifier() {
        PaymongoProperties properties = new PaymongoProperties(
                true, "TEST", "https://api.paymongo.com/v1",
                "sk_test_example", "", SECRET, "", 300);
        return new PaymongoWebhookSignatureVerifier(properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String sign(String timestamp, byte[] payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signed = (timestamp + "." + new String(payload, StandardCharsets.UTF_8))
                .getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(mac.doFinal(signed));
    }
}
