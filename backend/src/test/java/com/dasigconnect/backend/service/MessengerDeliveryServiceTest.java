package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.ObjectMapper;

class MessengerDeliveryServiceTest {

    private MessengerConnectionService connections;
    private MessengerDeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        connections = Mockito.mock(MessengerConnectionService.class);
        deliveryService = new MessengerDeliveryService(
                connections,
                new ObjectMapper(),
                true,
                "123456789",
                "test-token",
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
    void sendToPsid_whenCredentialsMissing_returnsFalse() {
        MessengerDeliveryService unconfiguredService = new MessengerDeliveryService(
                connections,
                new ObjectMapper(),
                false,
                "",
                "",
                "v25.0");

        boolean result = unconfiguredService.sendToPsid("psid-123", "Hello");
        assertThat(result).isFalse();
    }
}
