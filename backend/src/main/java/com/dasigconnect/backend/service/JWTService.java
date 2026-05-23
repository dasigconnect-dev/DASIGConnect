package com.dasigconnect.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.dasigconnect.backend.model.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JWTService {

    private final Clock clock;
    private final SecretKey signingKey;
    private final Duration accessTokenTtl;
    private final Map<String, Instant> blacklistedTokens = new ConcurrentHashMap<>();

    @Autowired
    public JWTService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-ttl-minutes:480}") long accessTokenTtlMinutes) {
        this(Clock.systemUTC(), secret, Duration.ofMinutes(accessTokenTtlMinutes));
    }

    JWTService(Clock clock, String secret, Duration accessTokenTtl) {
        this.clock = clock;
        this.signingKey = Keys.hmacShaKeyFor(sha256(secret));
        this.accessTokenTtl = accessTokenTtl;
    }

    public String generateAccessToken(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(accessTokenTtl);
        var builder = Jwts.builder()
                .subject(user.getId().toString())
                .claim("user_id", user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256);

        if (user.getInstitution() != null) {
            builder.claim("institution_id", user.getInstitution().getId().toString());
        }

        return builder.compact();
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank() || isBlacklisted(token)) {
            return false;
        }
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public Claims extractClaims(String token) {
        if (isBlacklisted(token)) {
            throw new JwtException("JWT has been invalidated");
        }
        return Jwts.parser()
                .verifyWith(signingKey)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Map<String, Object> verify(String token) {
        return Map.copyOf(extractClaims(token));
    }

    public void invalidateToken(String token) {
        Claims claims = extractClaims(token);
        blacklistedTokens.put(token, claims.getExpiration().toInstant());
    }

    private boolean isBlacklisted(String token) {
        Instant expiresAt = blacklistedTokens.get(token);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt.isBefore(clock.instant())) {
            blacklistedTokens.remove(token);
            return false;
        }
        return true;
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to prepare JWT signing key", ex);
        }
    }
}
