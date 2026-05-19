package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JWTServiceTest {

    private static final String SECRET = "test-secret-long-enough-for-hmac-sha256-minimum-size";
    private JWTService jwtService;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2025-06-01T00:00:00Z"), ZoneOffset.UTC);
        jwtService = new JWTService(fixedClock, SECRET, Duration.ofMinutes(60));
    }

    private User buildUser(UserRole role, UUID institutionId) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setRole(role);
        if (institutionId != null) {
            Institution inst = new Institution();
            inst.setId(institutionId);
            user.setInstitution(inst);
        }
        return user;
    }

    @Test
    void generateAndValidate_happyPath() {
        User user = buildUser(UserRole.contributor, UUID.randomUUID());
        String token = jwtService.generateAccessToken(user);
        assertThat(jwtService.validateToken(token)).isTrue();
    }

    @Test
    void token_containsRequiredClaims() {
        UUID institutionId = UUID.randomUUID();
        User user = buildUser(UserRole.validator, institutionId);

        String token = jwtService.generateAccessToken(user);
        Claims claims = jwtService.extractClaims(token);

        assertThat(claims.get("role", String.class)).isEqualTo("validator");
        assertThat(claims.get("user_id", String.class)).isEqualTo(user.getId().toString());
        assertThat(claims.get("email", String.class)).isEqualTo("user@example.com");
        assertThat(claims.get("institution_id", String.class)).isEqualTo(institutionId.toString());
    }

    @Test
    void administrator_token_hasNoInstitutionId() {
        User admin = buildUser(UserRole.administrator, null);
        String token = jwtService.generateAccessToken(admin);
        Claims claims = jwtService.extractClaims(token);
        assertThat(claims.get("institution_id")).isNull();
    }

    @Test
    void expiredToken_failsValidation() {
        Clock pastClock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
        JWTService pastService = new JWTService(pastClock, SECRET, Duration.ofSeconds(1));
        User user = buildUser(UserRole.contributor, null);
        String token = pastService.generateAccessToken(user);

        // validate with a clock far in the future
        Clock futureClock = Clock.fixed(Instant.parse("2024-01-02T00:00:00Z"), ZoneOffset.UTC);
        JWTService futureService = new JWTService(futureClock, SECRET, Duration.ofMinutes(60));
        assertThat(futureService.validateToken(token)).isFalse();
    }

    @Test
    void tamperedToken_failsValidation() {
        User user = buildUser(UserRole.contributor, null);
        String token = jwtService.generateAccessToken(user);
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(jwtService.validateToken(tampered)).isFalse();
    }

    @Test
    void nullAndBlankToken_failValidation() {
        assertThat(jwtService.validateToken(null)).isFalse();
        assertThat(jwtService.validateToken("")).isFalse();
        assertThat(jwtService.validateToken("   ")).isFalse();
    }

    @Test
    void invalidatedToken_failsValidation() {
        User user = buildUser(UserRole.contributor, null);
        String token = jwtService.generateAccessToken(user);
        assertThat(jwtService.validateToken(token)).isTrue();

        jwtService.invalidateToken(token);
        assertThat(jwtService.validateToken(token)).isFalse();
    }

    @Test
    void invalidatedToken_extractClaims_throwsJwtException() {
        User user = buildUser(UserRole.contributor, null);
        String token = jwtService.generateAccessToken(user);
        jwtService.invalidateToken(token);
        assertThatThrownBy(() -> jwtService.extractClaims(token)).isInstanceOf(JwtException.class);
    }
}
