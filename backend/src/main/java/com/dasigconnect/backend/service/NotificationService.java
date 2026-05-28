package com.dasigconnect.backend.service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.dasigconnect.backend.model.dto.notification.NotificationDto;
import com.dasigconnect.backend.model.dto.notification.NotificationPageDto;
import com.dasigconnect.backend.model.entity.Notification;
import com.dasigconnect.backend.model.entity.NotificationEventType;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.NotificationRepository;
import com.dasigconnect.backend.security.JwtUserDetails;

/**
 * Class-level @Transactional is intentionally absent.
 * subscribe() and dispatch() must never run inside a JPA transaction —
 * holding a DB connection across an SSE stream lifetime exhausts the
 * HikariCP pool (5-connection Supabase limit).
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    // 30-minute SSE timeout — prevents silent infinite connections
    private static final long SSE_TIMEOUT_MS = 30L * 60L * 1000L;

    private final NotificationRepository notificationRepository;
    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> list(JwtUserDetails user) {
        return notificationRepository.findTop50ByRecipientIdOrderByCreatedAtDesc(user.userId())
                .stream()
                .map(NotificationDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public NotificationPageDto history(int page, int pageSize, JwtUserDetails user) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        Page<Notification> result = notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(user.userId(), PageRequest.of(safePage, safeSize));
        List<NotificationDto> items = result.getContent().stream().map(NotificationDto::from).toList();
        return new NotificationPageDto(items, result.getTotalElements(), safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public long unreadCount(JwtUserDetails user) {
        return notificationRepository.countByRecipientIdAndReadAtIsNull(user.userId());
    }

    @Transactional
    public void markRead(UUID notificationId, JwtUserDetails user) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found."));
        if (!notification.getRecipient().getId().equals(user.userId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found.");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public void markAllRead(JwtUserDetails user) {
        notificationRepository.markAllRead(user.userId(), Instant.now());
    }

    /**
     * Opens an SSE stream for the authenticated user.
     * No @Transactional — acquiring a DB connection here would hold it for the
     * entire stream lifetime, exhausting the 5-connection pool.
     */
    public SseEmitter subscribe(JwtUserDetails user) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.computeIfAbsent(user.userId(), key -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> {
            removeEmitter(user.userId(), emitter);
            log.info("SSE stream completed for user {} — active streams: {}", user.userId(), activeStreamCount());
        });
        emitter.onTimeout(() -> {
            removeEmitter(user.userId(), emitter);
            log.info("SSE stream timed out for user {} — active streams: {}", user.userId(), activeStreamCount());
        });
        emitter.onError(ex -> {
            removeEmitter(user.userId(), emitter);
            log.debug("SSE stream error for user {}: {}", user.userId(), ex.getMessage());
        });

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException ex) {
            removeEmitter(user.userId(), emitter);
            return emitter;
        }

        log.info("SSE stream opened for user {} — active streams: {}", user.userId(), activeStreamCount());
        return emitter;
    }

    /**
     * Saves a notification and immediately dispatches it to any live SSE streams.
     * @Transactional covers only the DB write; dispatch() is a self-call that
     * runs in the same transaction for a brief moment — acceptable since SSE
     * sends are in-memory buffer operations, not blocking network I/O.
     */
    @Transactional
    public Notification createNotification(User recipient, NotificationEventType eventType, String message, String deepLink) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setEventType(eventType);
        notification.setMessage(message);
        notification.setDeepLink(deepLink);
        Notification saved = notificationRepository.save(notification);
        dispatch(saved);
        return saved;
    }

    /**
     * Pushes a notification to all live SSE emitters for the recipient.
     * No @Transactional — no DB access here. Broken emitters are removed safely.
     */
    public void dispatch(Notification notification) {
        List<SseEmitter> userEmitters = emitters.get(notification.getRecipient().getId());
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }
        NotificationDto dto = NotificationDto.from(notification);
        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(dto));
            } catch (IOException | IllegalStateException ex) {
                log.debug("Failed to send SSE to {}: {}", notification.getRecipient().getId(), ex.getMessage());
                removeEmitter(notification.getRecipient().getId(), emitter);
            }
        }
    }

    private void removeEmitter(UUID userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) {
            return;
        }
        userEmitters.remove(emitter);
        if (userEmitters.isEmpty()) {
            emitters.remove(userId);
        }
    }

    private int activeStreamCount() {
        return emitters.values().stream().mapToInt(List::size).sum();
    }
}
