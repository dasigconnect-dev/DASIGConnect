package com.dasigconnect.backend.model.dto.notification;

import java.time.Instant;
import java.util.UUID;

import com.dasigconnect.backend.model.entity.Notification;

public class NotificationDto {

    private UUID id;
    private String eventType;
    private String message;
    private String deepLink;
    private Instant readAt;
    private Instant createdAt;

    public static NotificationDto from(Notification notification) {
        NotificationDto dto = new NotificationDto();
        dto.id = notification.getId();
        dto.eventType = notification.getEventType().name();
        dto.message = notification.getMessage();
        dto.deepLink = notification.getDeepLink();
        dto.readAt = notification.getReadAt();
        dto.createdAt = notification.getCreatedAt();
        return dto;
    }

    public UUID getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getMessage() {
        return message;
    }

    public String getDeepLink() {
        return deepLink;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
