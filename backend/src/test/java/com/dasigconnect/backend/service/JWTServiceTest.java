package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.RevokedToken;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.model.entity.UserStatus;
import com.dasigconnect.backend.repository.RevokedTokenRepository;
import com.dasigconnect.backend.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Both revocation paths (single-token blacklist, account-wide
 * session_version bump) are now backed by repositories rather than
 * in-process maps — see JWTService. These tests fake the repositories with
 * a simple in-memory stand-in so the same assertions as before still hold,
 * plus new assertions specifically proving the persisted behavior: a
 * revocation made through one JWTService instance is honored by a second,
 * independently constructed instance sharing the same backing "database"
 * (modelling a restart / a second app instance).
 */
@ExtendWith(MockitoExtension.class)
class JWTServiceTest {

    private static final String SECRET = "test-secret-long-enough-for-hmac-sha256-minimum-size";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RevokedTokenRepository revokedTokenRepository;

    private final Set<String> revokedHashes = new HashSet<>();

    private JWTService jwtService;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2025-06-01T00:00:00Z"), ZoneOffset.UTC);
        jwtService = newService(fixedClock, Duration.ofMinutes(60));

        lenient().when(revokedTokenRepository.existsByTokenHashAndExpiresAtAfter(anyString(), any()))
                .thenAnswer(inv -> revokedHashes.contains(inv.getArgument(0, String.class)));
        lenient().when(revokedTokenRepository.save(any(RevokedToken.class))).thenAnswer(inv -> {
            RevokedToken saved = inv.getArgument(0);
            revokedHashes.add(saved.getTokenHash());
            return saved;
        });
    }

    /** A second instance sharing the same mocked repositories — models a fresh process/instance. */
    private JWTService newService(Clock clock, Duration ttl) {
        return new JWTService(clock, SECRET, ttl, userRepository, revokedTokenRepository);
    }

    private User buildActiveUser(UserRole role, UUID institutionId) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setRole(role);
        user.setAccountState(UserStatus.active);
        if (institutionId != null) {
            Institution inst = new Institution();
            inst.setId(institutionId);
            user.setInstitution(inst);
        }
        lenient().when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void generateAndValidate_happyPath() {
        User user = buildActiveUser(UserRole.contributor, UUID.randomUUID());
        String token = jwtService.generateAccessToken(user);
        assertThat(jwtService.validateToken(token)).isTrue();
    }

    @Test
    void token_containsRequiredClaims() {
        UUID institutionId = UUID.randomUUID();
        User user = buildActiveUser(UserRole.moderator, institutionId);

        String token = jwtService.generateAccessToken(user);
        Claims claims = jwtService.extractClaims(token);

        assertThat(claims.get("role", String.class)).isEqualTo("moderator");
        assertThat(claims.get("user_id", String.class)).isEqualTo(user.getId().toString());
        assertThat(claims.get("email", String.class)).isEqualTo("user@example.com");
        assertThat(claims.get("institution_id", String.class)).isEqualTo(institutionId.toString());
        assertThat(claims.get("session_version", Number.class).longValue()).isZero();
        assertThat(claims.get("admin", Boolean.class)).isFalse();
    }

    @Test
    void admin_token_hasNoInstitutionId() {
        User admin = buildActiveUser(UserRole.admin, null);
        String token = jwtService.generateAccessToken(admin);
        Claims claims = jwtService.extractClaims(token);
        assertThat(claims.get("institution_id")).isNull();
    }

    @Test
    void admin_token_containsAdminAndAdminOwnerClaims() {
        User admin = buildActiveUser(UserRole.admin, null);
        admin.setAdminOwner(true);

        String token = jwtService.generateAccessToken(admin);
        Claims claims = jwtService.extractClaims(token);

        assertThat(claims.get("admin", Boolean.class)).isTrue();
        assertThat(claims.get("admin_owner", Boolean.class)).isTrue();
    }

    @Test
    void expiredToken_failsValidation() {
        Clock pastClock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
        JWTService pastService = newService(pastClock, Duration.ofSeconds(1));
        User user = buildActiveUser(UserRole.contributor, null);
        String token = pastService.generateAccessToken(user);

        // validate with a clock far in the future
        Clock futureClock = Clock.fixed(Instant.parse("2024-01-02T00:00:00Z"), ZoneOffset.UTC);
        JWTService futureService = newService(futureClock, Duration.ofMinutes(60));
        assertThat(futureService.validateToken(token)).isFalse();
    }

    @Test
    void tamperedToken_failsValidation() {
        User user = buildActiveUser(UserRole.contributor, null);
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
        User user = buildActiveUser(UserRole.contributor, null);
        String token = jwtService.generateAccessToken(user);
        assertThat(jwtService.validateToken(token)).isTrue();

        jwtService.invalidateToken(token);
        assertThat(jwtService.validateToken(token)).isFalse();
    }

    @Test
    void invalidatedToken_extractClaims_throwsJwtException() {
        User user = buildActiveUser(UserRole.contributor, null);
        String token = jwtService.generateAccessToken(user);
        jwtService.invalidateToken(token);
        assertThatThrownBy(() -> jwtService.extractClaims(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void invalidateUserTokens_revokesExistingUserToken() {
        User user = buildActiveUser(UserRole.moderator, null);
        String token = jwtService.generateAccessToken(user);

        jwtService.invalidateUserTokens(user.getId());

        assertThat(jwtService.validateToken(token)).isFalse();
        assertThatThrownBy(() -> jwtService.extractClaims(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void deactivatedAccount_failsValidation_evenWithoutExplicitInvalidation() {
        // Session-version check alone wouldn't catch this — a token issued while
        // active, for an account since deactivated by some other path, must also
        // stop working immediately.
        User user = buildActiveUser(UserRole.contributor, null);
        String token = jwtService.generateAccessToken(user);
        assertThat(jwtService.validateToken(token)).isTrue();

        user.setAccountState(UserStatus.inactive);

        assertThat(jwtService.validateToken(token)).isFalse();
    }

    @Test
    void revocation_survivesAcrossServiceInstances_sameBackingRepositories() {
        // Models a backend restart (or a second instance): a fresh JWTService,
        // sharing the same repositories, must still see revocations made
        // through the original instance — the whole point of persisting this
        // instead of an in-process map.
        User user = buildActiveUser(UserRole.contributor, null);
        String userWideToken = jwtService.generateAccessToken(user);
        Clock oneSecondLater = Clock.fixed(fixedClock.instant().plusSeconds(1), ZoneOffset.UTC);
        String singleToken = newService(oneSecondLater, Duration.ofMinutes(60)).generateAccessToken(user);

        // Blacklist the single token before the account-wide revocation makes
        // every pre-bump token (including this one) invalid anyway.
        jwtService.invalidateToken(singleToken);
        jwtService.invalidateUserTokens(user.getId());

        JWTService freshInstance = newService(fixedClock, Duration.ofMinutes(60));
        assertThat(freshInstance.validateToken(userWideToken)).isFalse();
        assertThat(freshInstance.validateToken(singleToken)).isFalse();
    }

    @Test
    void logout_doesNotRevokeOtherTokensForTheSameAccount() {
        User user = buildActiveUser(UserRole.contributor, null);
        String deviceA = jwtService.generateAccessToken(user);
        // Distinct issuedAt so this is a genuinely different token, not a
        // byte-identical one from signing the same claims at the same instant.
        Clock oneSecondLater = Clock.fixed(fixedClock.instant().plusSeconds(1), ZoneOffset.UTC);
        String deviceB = newService(oneSecondLater, Duration.ofMinutes(60)).generateAccessToken(user);

        jwtService.invalidateToken(deviceA);

        assertThat(jwtService.validateToken(deviceA)).isFalse();
        assertThat(jwtService.validateToken(deviceB)).isTrue();
    }
}
