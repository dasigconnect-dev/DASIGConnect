package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.security.JwtUserDetails;

class MessengerConnectionServiceTest {

    private JdbcClient jdbc;
    private MessengerConnectionService service;

    @BeforeEach
    void setUp() {
        jdbc = Mockito.mock(JdbcClient.class, Mockito.RETURNS_DEEP_STUBS);
        service = new MessengerConnectionService(jdbc, "123456789");
    }

    @Test
    void createLinkCode_nonAdminRole_throwsForbidden() {
        JwtUserDetails contributor = new JwtUserDetails(UUID.randomUUID(), "contrib@test.edu", "contributor", UUID.randomUUID(), false);

        assertThatThrownBy(() -> service.createLinkCode(contributor))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only moderators and admins can connect Messenger.");
    }

    @Test
    void createLinkCode_moderatorRole_generatesCodeStartingWithCONNECT() {
        JwtUserDetails admin = new JwtUserDetails(UUID.randomUUID(), "admin@test.edu", "moderator", UUID.randomUUID(), false);

        var result = service.createLinkCode(admin);
        assertThat(result.code()).startsWith("CONNECT ");
        assertThat(result.expiresAt()).isNotNull();
    }

    @Test
    void createLinkCode_adminRole_generatesCodeStartingWithCONNECT() {
        JwtUserDetails superAdmin = new JwtUserDetails(UUID.randomUUID(), "superadmin@dost.gov.ph", "admin", null, true);

        var result = service.createLinkCode(superAdmin);
        assertThat(result.code()).startsWith("CONNECT ");
        assertThat(result.expiresAt()).isNotNull();
    }
}
