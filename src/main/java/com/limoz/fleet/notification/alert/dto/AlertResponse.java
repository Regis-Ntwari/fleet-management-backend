package com.limoz.fleet.notification.alert.dto;

import com.limoz.fleet.notification.domain.NotificationSeverity;
import com.limoz.fleet.notification.alert.domain.AlertStatus;
import com.limoz.fleet.notification.alert.domain.AlertType;

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
