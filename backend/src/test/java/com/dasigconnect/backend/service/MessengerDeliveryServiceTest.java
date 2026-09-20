package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.dasigconnect.backend.model.entity.FacebookPageToken;
import com.dasigconnect.backend.repository.FacebookPageTokenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class MessengerDeliveryServiceTest {

    private MessengerConnectionService connections;
    private TokenEncryptionService tokenEncryptionService;
    private FacebookPageTokenRepository pageTokenRepository;
    private MessengerDeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        connections = Mockito.mock(MessengerConnectionService.class);
        tokenEncryptionService = Mockito.mock(TokenEncryptionService.class);
        pageTokenRepository = Mockito.mock(FacebookPageTokenRepository.class);
        deliveryService = new MessengerDeliveryService(
                connections,
                new ObjectMapper(),
                tokenEncryptionService,
                pageTokenRepository,
                true,
                "v25.0");
    }

    @Test
    void sendToUser_whenUserNotLinked_silentlyReturnsFalse() {
        UUID userId = UUID.randomUUID();
        when(connections.psidFor(userId)).thenReturn(Optional.empty());

        boolean result = deliveryService.sendToUser(userId, "Hello from DASIGConnect");
        assertThat(result).isFalse();
    }

    @Test
    void sendToUser_whenUserIdNull_returnsFalse() {
        boolean result = deliveryService.sendToUser(null, "Hello");
        assertThat(result).isFalse();
    }

    @Test
    void sendToPsid_whenMessengerDisabled_returnsFalse() {
        MessengerDeliveryService disabledService = new MessengerDeliveryService(
                connections,
                new ObjectMapper(),
                tokenEncryptionService,
                pageTokenRepository,
                false,
                "v25.0");

        boolean result = disabledService.sendToPsid("psid-123", "Hello");
        assertThat(result).isFalse();
        Mockito.verifyNoInteractions(pageTokenRepository);
    }

    @Test
    void sendToPsid_whenNoActivePageToken_returnsFalse() {
        when(pageTokenRepository.findFirstByIsActiveTrue()).thenReturn(Optional.empty());

        boolean result = deliveryService.sendToPsid("psid-123", "Hello");
        assertThat(result).isFalse();
    }

    @Test
    void sendToPsid_whenActivePageTokenExpired_returnsFalse() {
        FacebookPageToken token = new FacebookPageToken();
        token.setPageId("123456789");
        token.setEncryptedToken("irrelevant");
        token.setExpiresAt(Instant.now().minusSeconds(60));
        when(pageTokenRepository.findFirstByIsActiveTrue()).thenReturn(Optional.of(token));

        boolean result = deliveryService.sendToPsid("psid-123", "Hello");
        assertThat(result).isFalse();
        Mockito.verifyNoInteractions(tokenEncryptionService);
    }

    @Test
    void sendToPsid_whenTokenCannotBeDecrypted_returnsFalse() {
        FacebookPageToken token = new FacebookPageToken();
        token.setPageId("123456789");
        token.setEncryptedToken("corrupted");
        when(pageTokenRepository.findFirstByIsActiveTrue()).thenReturn(Optional.of(token));
        when(tokenEncryptionService.decryptToken("corrupted"))
                .thenThrow(new RuntimeException("bad ciphertext"));

        boolean result = deliveryService.sendToPsid("psid-123", "Hello");
        assertThat(result).isFalse();
    }
}
