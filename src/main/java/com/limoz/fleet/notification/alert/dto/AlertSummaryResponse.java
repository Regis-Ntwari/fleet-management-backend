package com.limoz.fleet.notification.alert.dto;

import com.limoz.fleet.notification.domain.NotificationSeverity;
import com.limoz.fleet.notification.alert.domain.AlertStatus;
import com.limoz.fleet.notification.alert.domain.AlertType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Dashboard widget: open alerts by severity, all alerts by status, most frequent open alert types. */
public record AlertSummaryResponse(
        long open,
        long critical,
        long warning,
        long info,
        Map<NotificationSeverity, Long> bySeverity,
        Map<AlertStatus, Long> byStatus,
        List<TypeCount> topTypes,
        Instant generatedAt) {

    public record TypeCount(AlertType type, long count) {}
}
