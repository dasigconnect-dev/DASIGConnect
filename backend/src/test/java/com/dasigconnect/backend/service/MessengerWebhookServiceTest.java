package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.ObjectMapper;

class MessengerWebhookServiceTest {

    private static final String VERIFY_TOKEN = "verify-secret";
    private static final String APP_SECRET = "app-secret";

    private final MessengerWebhookService service =
            new MessengerWebhookService(
                    VERIFY_TOKEN,
                    APP_SECRET,
                    new ObjectMapper(),
                    Mockito.mock(MessengerConnectionService.class),
                    Mockito.mock(MessengerDeliveryService.class));

    @Test
    void verification_requiresSubscribeModeAndMatchingToken() {
        assertThat(service.isVerificationRequestValid("subscribe", VERIFY_TOKEN)).isTrue();
        assertThat(service.isVerificationRequestValid("subscribe", "wrong")).isFalse();
        assertThat(service.isVerificationRequestValid("unsubscribe", VERIFY_TOKEN)).isFalse();
        assertThat(service.isVerificationRequestValid("subscribe", null)).isFalse();
    }

    @Test
    void signature_acceptsCorrectHmacSha256() throws Exception {
        byte[] payload = "{\"object\":\"page\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(service.isSignatureValid(payload, sign(payload))).isTrue();
    }

    @Test
    void signature_rejectsMissingMalformedAndIncorrectValues() {
        byte[] payload = "{\"object\":\"page\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(service.isSignatureValid(payload, null)).isFalse();
        assertThat(service.isSignatureValid(payload, "sha1=abc")).isFalse();
        assertThat(service.isSignatureValid(payload, "sha256=not-hex")).isFalse();
        assertThat(service.isSignatureValid(payload, "sha256=00")).isFalse();
    }

    private static String sign(byte[] payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload));
    }
}
