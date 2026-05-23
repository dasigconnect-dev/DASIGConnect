package com.dasigconnect.backend.model.dto.notification;

import java.util.List;

public record NotificationPageDto(
        List<NotificationDto> items,
        long totalCount,
        int page,
        int pageSize) {}
