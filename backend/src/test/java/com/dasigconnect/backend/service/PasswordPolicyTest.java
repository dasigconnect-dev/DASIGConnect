package com.dasigconnect.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @Test
    void validate_acceptsStrongPassword() {
        assertThatCode(() -> PasswordPolicy.validate(
                "Riv3r!Moonlight",
                "user@example.com",
                "Lerah",
                "Caones"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_rejectsIdentityPassword() {
        assertThatThrownBy(() -> PasswordPolicy.validate(
                "Caones!Secure47",
                "user@example.com",
                "Lerah",
                "Caones"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void validate_rejectsCommonSequence() {
        assertThatThrownBy(() -> PasswordPolicy.validate(
                "Bright123!Moon",
                "user@example.com",
                "Lerah",
                "Caones"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
