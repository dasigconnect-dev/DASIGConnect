package com.dasigconnect.backend.exception;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.AuditLogService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Writes {@code ACCESS_DENIED} audit rows for 403s, with three noise filters so
 * the log stays a signal and not a scanner dump:
 *
 * <ol>
 *   <li><b>Authenticated only</b> — a row is written only when a real JWT
 *       principal is on the request. Anonymous / bad-token / expired-token 403s
 *       (i.e. every unauthenticated probe) are ignored; those are auth failures,
 *       already covered by {@code LOGIN_FAILED} / {@code ACCOUNT_LOCKED}.</li>
 *   <li><b>Path normalized</b> — {@code /submissions/{uuid}} collapses to
 *       {@code /submissions/:id} so walking sequential ids can't inflate the
 *       log.</li>
 *   <li><b>Throttled</b> — at most one row per (actor, method, normalized path)
 *       per 10 minutes, held in a bounded in-memory map (lost on restart —
 *       acceptable).</li>
 * </ol>
 *
 * {@code ACCESS_DENIED} is a security signal and is never pruned by
 * {@code AuditLogRetentionJob}; the throttle is what bounds its volume.
 */
@Component
public class AccessDeniedAuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(AccessDeniedAuditRecorder.class);

    private static final long WINDOW_MS = Duration.ofMinutes(10).toMillis();
    private static final int MAX_KEYS = 2000;
    private static final int MAX_REASON_CHARS = 200;

    /** A single path segment that is an opaque id (UUID or all-digits). */
    private static final Pattern ID_SEGMENT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|\\d+");

    private final AuditLogService auditLogService;
    private final Map<String, Long> lastSeen = new ConcurrentHashMap<>();

    public AccessDeniedAuditRecorder(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * @param reason short, non-PII explanation (e.g. the {@code ResponseStatusException}
     *               reason text, or {@code "method-security"} for a {@code @PreAuthorize}
     *               denial). May be {@code null}.
     */
    public void record(HttpServletRequest request, String reason) {
        try {
            if (request == null) {
                return;
            }
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()
                    || !(auth.getPrincipal() instanceof JwtUserDetails user)) {
                return; // unauthenticated / anonymous — not an authorization signal
            }

            String method = request.getMethod();
            String path = normalize(request.getRequestURI());
            String key = user.userId() + " " + method + " " + path;

            long now = System.currentTimeMillis();
            Long prev = lastSeen.get(key);
            if (prev != null && now - prev < WINDOW_MS) {
                return; // already recorded this (actor, method, path) recently
            }
            lastSeen.put(key, now);
            evictIfLarge(now);

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("method", method);
            metadata.put("path", path);
            if (reason != null && !reason.isBlank()) {
                metadata.put("reason", reason.length() > MAX_REASON_CHARS
                        ? reason.substring(0, MAX_REASON_CHARS) : reason);
            }

            auditLogService.recordByActorId(
                    user.userId(), "ACCESS_DENIED",
                    clientIp(request), request.getHeader("User-Agent"),
                    null, metadata);
        } catch (RuntimeException ex) {
            // never let audit bookkeeping change the 403 the caller is about to send
            log.debug("ACCESS_DENIED audit skipped: {}", ex.getMessage());
        }
    }

    private void evictIfLarge(long now) {
        if (lastSeen.size() <= MAX_KEYS) {
            return;
        }
        lastSeen.entrySet().removeIf(e -> now - e.getValue() >= WINDOW_MS);
        if (lastSeen.size() > MAX_KEYS) {
            lastSeen.clear();
        }
    }

    static String normalize(String uri) {
        if (uri == null || uri.isBlank()) {
            return "/";
        }
        StringBuilder sb = new StringBuilder();
        for (String segment : uri.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            sb.append('/').append(ID_SEGMENT.matcher(segment).matches() ? ":id" : segment);
        }
        return sb.isEmpty() ? "/" : sb.toString();
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
