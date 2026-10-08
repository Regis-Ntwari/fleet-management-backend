package com.limoz.fleet.notification.domain;

import com.limoz.fleet.common.event.Severity;

/** Severity of a notification / alert (CHECK constraint on notifications.severity and alerts.severity). */
public enum NotificationSeverity {
    INFO, WARNING, CRITICAL;

    public static NotificationSeverity from(Severity severity) {
        return severity == null ? INFO : valueOf(severity.name());
    }

    public boolean atLeast(NotificationSeverity other) {
        return ordinal() >= other.ordinal();
    }
}
