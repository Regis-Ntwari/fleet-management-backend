package com.limoz.fleet.notification.alert.dto;

import com.limoz.fleet.notification.NotificationSeverity;
import com.limoz.fleet.notification.alert.AlertStatus;
import com.limoz.fleet.notification.alert.AlertType;

public record AlertFilter(AlertStatus status, NotificationSeverity severity, AlertType type, String entityType) {}
