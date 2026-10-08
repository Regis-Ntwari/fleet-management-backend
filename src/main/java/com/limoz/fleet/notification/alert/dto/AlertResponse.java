package com.limoz.fleet.notification.alert.dto;

import com.limoz.fleet.notification.NotificationSeverity;
import com.limoz.fleet.notification.alert.AlertStatus;
import com.limoz.fleet.notification.alert.AlertType;

import java.time.Instant;

public record AlertResponse(
        Long id,
        AlertType type,
        NotificationSeverity severity,
        String title,
        String message,
        String entityType,
        Long entityId,
        String entityReference,
        String linkPath,
        AlertStatus status,
        Instant firstDetectedAt,
        Instant lastDetectedAt,
        Long acknowledgedByUserId,
        String acknowledgedByName,
        Instant acknowledgedAt,
        Instant resolvedAt,
        String resolutionNote) {}
