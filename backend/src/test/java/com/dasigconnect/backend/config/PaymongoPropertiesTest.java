package com.dasigconnect.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PaymongoPropertiesTest {

    @Test
    void validate_disabledIntegrationAllowsMissingCredentials() {
        PaymongoProperties properties = properties(false, "TEST", "", "", "", "");

        properties.validate();

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.liveMode()).isFalse();
    }

    @Test
    void validate_testModeAcceptsMatchingCredentials() {
        PaymongoProperties properties = properties(
                true, "TEST", "sk_test_example", "", "whsk_test_example", "");

        properties.validate();

        assertThat(properties.secretKey()).isEqualTo("sk_test_example");
        assertThat(properties.webhookSecret()).isEqualTo("whsk_test_example");
    }

    @Test
    void validate_testModeRejectsLiveKey() {
        PaymongoProperties properties = properties(
                true, "TEST", "sk_live_example", "", "whsk_test_example", "");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TEST mode requires");
    }

    @Test
    void validate_enabledIntegrationRequiresWebhookSecret() {
        PaymongoProperties properties = properties(true, "TEST", "sk_test_example", "", "", "");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("webhook verification secret");
    }

    @Test
    void validate_liveModeRejectsNonPaymongoEndpoint() {
        PaymongoProperties properties = new PaymongoProperties(
                true, "LIVE", "http://localhost:8081/v1", "", "sk_live_example",
                "", "whsk_live_example", 300);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("official HTTPS API endpoint");
    }

    private static PaymongoProperties properties(
            boolean enabled,
            String mode,
            String testKey,
            String liveKey,
            String testWebhookSecret,
            String liveWebhookSecret) {
        return new PaymongoProperties(
                enabled, mode, "https://api.paymongo.com/v1", testKey, liveKey,
                testWebhookSecret, liveWebhookSecret, 300);
    }
}
