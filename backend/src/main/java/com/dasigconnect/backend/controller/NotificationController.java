package com.dasigconnect.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.dasigconnect.backend.model.dto.notification.NotificationDto;
import com.dasigconnect.backend.model.dto.notification.NotificationPageDto;
import com.dasigconnect.backend.model.dto.notification.NotificationUnreadCountDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.NotificationService;

/**
 * REST endpoints for UC-2.3: Notification delivery (SSE + read state). Base
 * path: /api/v1/notifications
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NotificationDto>> list(
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(notificationService.list(user));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationUnreadCountDto> unreadCount(
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(new NotificationUnreadCountDto(notificationService.unreadCount(user)));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails user) {
        notificationService.markRead(id, user);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAllRead(
            @AuthenticationPrincipal JwtUserDetails user) {
        notificationService.markAllRead(user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationPageDto> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(notificationService.history(page, pageSize, user));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SseEmitter stream(@AuthenticationPrincipal JwtUserDetails user) {
        return notificationService.subscribe(user);
    }
}
