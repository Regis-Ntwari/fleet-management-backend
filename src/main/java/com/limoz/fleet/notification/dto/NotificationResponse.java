package com.limoz.fleet.notification.dto;

import com.limoz.fleet.notification.NotificationSeverity;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        String type,
        NotificationSeverity severity,
        String title,
        String message,
        String entityType,
        Long entityId,
        String linkPath,
        boolean read,
        Instant readAt,
        Instant createdAt) {}
