package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenEncryptionServiceTest {

    private TokenEncryptionService service;
    private static final String APP_SECRET = "super-secret-test-app-secret-123456";

    @BeforeEach
    void setUp() {
        service = new TokenEncryptionService(APP_SECRET);
    }

    @Test
    void isConfigured_withSecret_returnsTrue() {
        assertThat(service.isConfigured()).isTrue();
    }

    @Test
    void isConfigured_withNullOrBlank_returnsFalse() {
        TokenEncryptionService nullSecretService = new TokenEncryptionService(null);
        TokenEncryptionService blankSecretService = new TokenEncryptionService("   ");

        assertThat(nullSecretService.isConfigured()).isFalse();
        assertThat(blankSecretService.isConfigured()).isFalse();
    }

    @Test
    void encryptAndDecryptToken_roundTrip_matchesOriginal() {
        String token = "EAABwzLIX564BAO7testToken1234567890abcdefghijklmnopqrstuvwxyz";

        String encrypted = service.encryptToken(token);

        assertThat(encrypted).isNotNull();
        assertThat(encrypted).contains(":");
        assertThat(encrypted).isNotEqualTo(token);

        String decrypted = service.decryptToken(encrypted);
        assertThat(decrypted).isEqualTo(token);
    }

    @Test
    void encryptToken_producesDifferentIVEachTime() {
        String token = "same-plaintext-token";

        String firstEncrypted = service.encryptToken(token);
        String secondEncrypted = service.encryptToken(token);

        assertThat(firstEncrypted).isNotEqualTo(secondEncrypted);
        assertThat(service.decryptToken(firstEncrypted)).isEqualTo(token);
        assertThat(service.decryptToken(secondEncrypted)).isEqualTo(token);
    }

    @Test
    void decryptToken_invalidFormat_throwsException() {
        assertThatThrownBy(() -> service.decryptToken("invalid-format-without-colon"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Token decryption failed");
    }

    @Test
    void decryptToken_tamperedCiphertext_throwsException() {
        String token = "valid-token";
        String encrypted = service.encryptToken(token);
        String[] parts = encrypted.split(":");
        String tampered = parts[0] + ":" + parts[1].substring(0, parts[1].length() - 4) + "AAAA";

        assertThatThrownBy(() -> service.decryptToken(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Token decryption failed");
    }

    @Test
    void encryptToken_withBlankSecret_throwsException() {
        TokenEncryptionService unconfiguredService = new TokenEncryptionService("");

        // When secret is empty or blank, cipher operations fail or throw
        assertThatThrownBy(() -> unconfiguredService.encryptToken(null))
                .isInstanceOf(IllegalStateException.class);
    }
}
