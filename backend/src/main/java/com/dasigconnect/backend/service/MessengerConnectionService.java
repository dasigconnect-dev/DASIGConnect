package com.dasigconnect.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.model.dto.notification.MessengerConnectionDto;
import com.dasigconnect.backend.model.dto.notification.MessengerLinkCodeDto;
import com.dasigconnect.backend.security.JwtUserDetails;

@Service
public class MessengerConnectionService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final JdbcClient jdbc;
    private final String pageId;

    public MessengerConnectionService(
            JdbcClient jdbc,
            @Value("${app.messenger.page-id:${app.facebook.page-id:}}") String pageId) {
        this.jdbc = jdbc;
        this.pageId = pageId;
    }

    @Transactional
    public MessengerLinkCodeDto createLinkCode(JwtUserDetails user) {
        if (!"administrator".equals(user.role()) && !"super_administrator".equals(user.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only administrators can connect Messenger.");
        }
        String code = randomCode();
        Instant expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES);
        jdbc.sql("DELETE FROM messenger_connections WHERE user_id = :userId")
                .param("userId", user.userId()).update();
        jdbc.sql("""
                INSERT INTO messenger_connections
                    (user_id, page_id, enabled, link_code_hash, link_code_expires_at, updated_at)
                VALUES (:userId, :pageId, FALSE, :codeHash, :expiresAt, NOW())
                """)
                .param("userId", user.userId())
                .param("pageId", pageId != null ? pageId : "")
                .param("codeHash", hash(code))
                .param("expiresAt", Timestamp.from(expiresAt))
                .update();
        return new MessengerLinkCodeDto("CONNECT " + code, expiresAt);
    }

    @Transactional(readOnly = true)
    public MessengerConnectionDto status(JwtUserDetails user) {
        return jdbc.sql("""
                SELECT page_scoped_user_id, enabled, linked_at
                FROM messenger_connections WHERE user_id = :userId
                """)
                .param("userId", user.userId())
                .query((rs, row) -> new MessengerConnectionDto(
                        rs.getString("page_scoped_user_id") != null,
                        rs.getBoolean("enabled"),
                        rs.getTimestamp("linked_at") == null
                                ? null : rs.getTimestamp("linked_at").toInstant()))
                .optional()
                .orElse(new MessengerConnectionDto(false, false, null));
    }

    @Transactional
    public void disconnect(JwtUserDetails user) {
        jdbc.sql("DELETE FROM messenger_connections WHERE user_id = :userId")
                .param("userId", user.userId())
                .update();
    }

    @Transactional
    public Optional<UUID> link(String code, String psid) {
        return jdbc.sql("""
                SELECT user_id FROM messenger_connections
                WHERE link_code_hash = :codeHash
                  AND link_code_expires_at > NOW()
                """)
                .param("codeHash", hash(code.toUpperCase(Locale.ROOT)))
                .query(UUID.class)
                .optional()
                .map(userId -> {
                    jdbc.sql("""
                            UPDATE messenger_connections
                            SET page_scoped_user_id = :psid, enabled = TRUE,
                                linked_at = NOW(), last_interaction_at = NOW(),
                                link_code_hash = NULL, link_code_expires_at = NULL,
                                updated_at = NOW()
                            WHERE user_id = :userId
                            """)
                            .param("psid", psid)
                            .param("userId", userId)
                            .update();
                    return userId;
                });
    }

    @Transactional
    public void recordInteraction(String psid) {
        jdbc.sql("""
                UPDATE messenger_connections
                SET last_interaction_at = NOW(), updated_at = NOW()
                WHERE page_scoped_user_id = :psid
                """).param("psid", psid).update();
    }

    @Transactional(readOnly = true)
    public Optional<String> psidFor(UUID userId) {
        return jdbc.sql("""
                SELECT page_scoped_user_id FROM messenger_connections
                WHERE user_id = :userId AND enabled = TRUE
                """).param("userId", userId).query(String.class).optional();
    }

    private static String randomCode() {
        StringBuilder result = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            result.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return result.toString();
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
