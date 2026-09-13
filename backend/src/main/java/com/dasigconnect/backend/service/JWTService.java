package com.dasigconnect.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dasigconnect.backend.model.entity.RevokedToken;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserStatus;
import com.dasigconnect.backend.repository.RevokedTokenRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.TokenHashUtils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Issues and validates JWT access tokens. Both revocation paths are
 * persisted (Postgres), not held in process memory, so a backend restart or
 * a second instance never re-admits a token that should be dead:
 *
 * <ul>
 *   <li>{@link #invalidateToken} — a single logged-out token. Its SHA-256
 *       hash is stored in {@code revoked_tokens} (never the raw token) and
 *       checked on every subsequent validation.</li>
 *   <li>{@link #invalidateUserTokens} — every token an account holds, at
 *       once. Bumps {@code users.session_version}; a token's baked-in
 *       {@code session_version} claim is compared against the current DB
 *       value on every validation, so it stops working immediately
 *       regardless of when it expires.</li>
 * </ul>
 */
@Service
public class JWTService {

    private final Clock clock;
    private final SecretKey signingKey;
    private final Duration accessTokenTtl;
    private final UserRepository userRepository;
    private final RevokedTokenRepository revokedTokenRepository;

    @Autowired
    public JWTService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-ttl-minutes:480}") long accessTokenTtlMinutes,
            UserRepository userRepository,
            RevokedTokenRepository revokedTokenRepository) {
        this(Clock.systemUTC(), secret, Duration.ofMinutes(accessTokenTtlMinutes), userRepository, revokedTokenRepository);
    }

    JWTService(
            Clock clock,
            String secret,
            Duration accessTokenTtl,
            UserRepository userRepository,
            RevokedTokenRepository revokedTokenRepository) {
        this.clock = clock;
        this.signingKey = Keys.hmacShaKeyFor(sha256(secret));
        this.accessTokenTtl = accessTokenTtl;
        this.userRepository = userRepository;
        this.revokedTokenRepository = revokedTokenRepository;
    }

    public String generateAccessToken(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(accessTokenTtl);
        var builder = Jwts.builder()
                .subject(user.getId().toString())
                .claim("user_id", user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("session_version", user.getSessionVersion())
                .claim("admin",
                        user.getRole() == com.dasigconnect.backend.model.entity.UserRole.admin)
                .claim("admin_owner", user.isAdminOwner())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256);

        if (user.getInstitution() != null) {
            builder.claim("institution_id", user.getInstitution().getId().toString());
        }

        return builder.compact();
    }

    @Transactional(readOnly = true)
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public Claims extractClaims(String token) {
        if (isBlacklisted(token)) {
            throw new JwtException("JWT has been invalidated");
        }
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        assertUserTokenNotRevoked(claims);
        return claims;
    }

    public Map<String, Object> verify(String token) {
        return Map.copyOf(extractClaims(token));
    }

    @Transactional
    public void invalidateToken(String token) {
        Claims claims = extractClaims(token);
        String hash = TokenHashUtils.sha256Hex(token);
        if (revokedTokenRepository.existsByTokenHashAndExpiresAtAfter(hash, clock.instant())) {
            return;
        }
        RevokedToken revoked = new RevokedToken();
        revoked.setTokenHash(hash);
        revoked.setExpiresAt(claims.getExpiration().toInstant());
        revokedTokenRepository.save(revoked);
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    /**
     * Revokes every token the account currently holds, immediately — not
     * just going forward. Bumps {@code users.session_version}; since the
     * bump runs against whatever entity instance is already managed in the
     * caller's transaction (same persistence context), it composes safely
     * with a caller that loads, mutates, and later saves the same user row
     * in the same transaction (e.g. erasePersonalData).
     */
    @Transactional
    public void invalidateUserTokens(UUID userId) {
        if (userId == null) {
            return;
        }
        userRepository.findById(userId).ifPresent(user -> {
            user.setSessionVersion(user.getSessionVersion() + 1);
            userRepository.save(user);
        });
    }

    private void assertUserTokenNotRevoked(Claims claims) {
        String userIdClaim = claims.get("user_id", String.class);
        if (userIdClaim == null || userIdClaim.isBlank()) {
            return;
        }
        UUID userId;
        try {
            userId = UUID.fromString(userIdClaim);
        } catch (IllegalArgumentException ex) {
            return;
        }
        long tokenSessionVersion = claims.get("session_version", Number.class) == null
                ? -1 : claims.get("session_version", Number.class).longValue();

        User user = userRepository.findById(userId).orElse(null);
        if (user == null
                || user.getAccountState() != UserStatus.active
                || user.getSessionVersion() != tokenSessionVersion) {
            throw new JwtException("Session has been revoked");
        }
    }

    private boolean isBlacklisted(String token) {
        String hash = TokenHashUtils.sha256Hex(token);
        return revokedTokenRepository.existsByTokenHashAndExpiresAtAfter(hash, clock.instant());
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to prepare JWT signing key", ex);
        }
    }
}
